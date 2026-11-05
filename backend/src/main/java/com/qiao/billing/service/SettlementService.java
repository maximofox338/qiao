package com.qiao.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.service.UserService;
import com.qiao.billing.domain.SettlementBatch;
import com.qiao.billing.domain.SettlementBatchMapper;
import com.qiao.billing.domain.SettlementBatchStatus;
import com.qiao.billing.domain.SettlementLine;
import com.qiao.billing.domain.SettlementLineMapper;
import com.qiao.billing.domain.SettlementLineStatus;
import com.qiao.billing.web.LinePaidRequest;
import com.qiao.billing.web.SettlementBatchView;
import com.qiao.billing.web.SettlementLineView;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.money.Money;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.contract.service.MilestoneRef;
import com.qiao.contract.service.MilestoneService;
import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monthly settlement for the PRC partner: DRAFT → (CSV handed over) EXPORTED → (every line paid) PAID.
 * A batch takes every ACCEPTED milestone with a PAID payment that is not yet in a batch.
 */
@Service
public class SettlementService {

    private final SettlementBatchMapper batchMapper;
    private final SettlementLineMapper lineMapper;
    private final PaymentService paymentService;
    private final MilestoneService milestoneService;
    private final EngineerProfileService profileService;
    private final ProjectService projectService;
    private final UserService userService;
    private final AuditService auditService;
    private final Clock clock;

    public SettlementService(SettlementBatchMapper batchMapper, SettlementLineMapper lineMapper,
                             PaymentService paymentService, MilestoneService milestoneService,
                             EngineerProfileService profileService, ProjectService projectService,
                             UserService userService, AuditService auditService, Clock clock) {
        this.batchMapper = batchMapper;
        this.lineMapper = lineMapper;
        this.paymentService = paymentService;
        this.milestoneService = milestoneService;
        this.profileService = profileService;
        this.projectService = projectService;
        this.userService = userService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SettlementBatchView create(String period, long adminId) {
        Set<Long> paid = paymentService.paidMilestoneIds();
        List<MilestoneRef> milestones = milestoneService.lockSettleable().stream()
                .filter(m -> paid.contains(m.milestoneId()))
                .toList();
        if (milestones.isEmpty()) {
            throw new ApiException(ErrorCode.NOTHING_TO_SETTLE);
        }
        Map<Long, EngineerProfile> profiles = profileService.byUserIds(
                milestones.stream().map(MilestoneRef::engineerUserId).collect(Collectors.toSet()));
        SettlementBatch batch = new SettlementBatch();
        batch.setPeriod(period);
        batch.setStatus(SettlementBatchStatus.DRAFT);
        batch.setCreatedByAdminId(adminId);
        batchMapper.insert(batch);
        for (MilestoneRef milestone : milestones) {
            EngineerProfile profile = profiles.get(milestone.engineerUserId());
            SettlementLine line = new SettlementLine();
            line.setBatchId(batch.getId());
            line.setContractMilestoneId(milestone.milestoneId());
            line.setEngineerUserId(milestone.engineerUserId());
            line.setPartnerRef(profile.getPartnerRef());
            line.setAlias(profile.getAlias());
            line.setTalentAmountUsd(milestone.talentAmountUsd());
            line.setStatus(SettlementLineStatus.PENDING);
            lineMapper.insert(line);
            milestoneService.markSettled(milestone.milestoneId(), line.getId());
        }
        SettlementBatchView view = view(batch, true);
        auditService.log(Actor.admin(adminId), AuditAction.SETTLEMENT_CREATED, "settlement_batch", batch.getId(),
                Map.of("period", period, "lines", view.lineCount(), "totalTalentUsd", view.totalTalentUsd()));
        return view;
    }

    public List<SettlementBatchView> list(SettlementBatchStatus status) {
        return batchMapper.selectList(new LambdaQueryWrapper<SettlementBatch>()
                        .eq(status != null, SettlementBatch::getStatus, status)
                        .orderByDesc(SettlementBatch::getId))
                .stream().map(b -> view(b, false)).toList();
    }

    public SettlementBatchView get(long batchId) {
        return view(requireBatch(batchId), true);
    }

    /**
     * CSV for the partner: {@code line_id, alias, partner_ref, engineer_email, contract_milestone_id, project_title,
     * talent_amount_usd}. UTF-8 with BOM so spreadsheet tools detect the encoding.
     */
    public String exportCsv(long batchId, long adminId) {
        SettlementBatchView batch = get(batchId);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("line_id,alias,partner_ref,engineer_email,contract_milestone_id,project_title,talent_amount_usd\r\n");
        for (SettlementLineView line : batch.lines()) {
            csv.append(line.lineId()).append(',')
                    .append(csvField(line.alias())).append(',')
                    .append(csvField(line.partnerRef())).append(',')
                    .append(csvField(line.engineerEmail())).append(',')
                    .append(line.contractMilestoneId()).append(',')
                    .append(csvField(line.projectTitle())).append(',')
                    .append(line.talentAmountUsd().toPlainString()).append("\r\n");
        }
        auditService.log(Actor.admin(adminId), AuditAction.SETTLEMENT_CSV_EXPORTED, "settlement_batch", batchId,
                Map.of("lines", batch.lineCount()));
        return csv.toString();
    }

    @Transactional
    public SettlementBatchView markExported(long batchId, long adminId) {
        SettlementBatch batch = batchMapper.selectByIdForUpdate(batchId);
        if (batch == null) {
            throw ApiException.notFound("Settlement batch");
        }
        if (batch.getStatus() != SettlementBatchStatus.DRAFT) {
            throw ApiException.invalidState("Batch is not a draft");
        }
        batch.setStatus(SettlementBatchStatus.EXPORTED);
        batch.setExportedAt(clock.instant());
        batchMapper.updateById(batch);
        auditService.log(Actor.admin(adminId), AuditAction.SETTLEMENT_MARKED_EXPORTED, "settlement_batch", batchId);
        return view(batch, false);
    }

    // ------------------------------------------------------------------ partner API

    public List<SettlementBatchView> partnerList(SettlementBatchStatus status) {
        if (status == SettlementBatchStatus.DRAFT) {
            return List.of();
        }
        List<SettlementBatchView> batches = batchMapper.selectList(new LambdaQueryWrapper<SettlementBatch>()
                        .eq(status != null, SettlementBatch::getStatus, status)
                        .ne(SettlementBatch::getStatus, SettlementBatchStatus.DRAFT)
                        .orderByDesc(SettlementBatch::getId))
                .stream().map(b -> view(b, false)).toList();
        auditService.log(Actor.partner(), AuditAction.PARTNER_SETTLEMENTS_LISTED, "settlement_batch", null,
                status == null ? Map.of() : Map.of("status", status.name()));
        return batches;
    }

    public SettlementBatchView partnerGet(long batchId) {
        SettlementBatch batch = requireBatch(batchId);
        if (batch.getStatus() == SettlementBatchStatus.DRAFT) {
            throw ApiException.notFound("Settlement batch");
        }
        auditService.log(Actor.partner(), AuditAction.PARTNER_SETTLEMENT_VIEWED, "settlement_batch", batchId);
        return view(batch, true);
    }

    @Transactional
    public SettlementLineView markLinePaid(long batchId, long lineId, LinePaidRequest request) {
        SettlementBatch batch = batchMapper.selectByIdForUpdate(batchId);
        if (batch == null || batch.getStatus() == SettlementBatchStatus.DRAFT) {
            throw ApiException.notFound("Settlement batch");
        }
        SettlementLine line = lineMapper.selectByIdForUpdate(lineId);
        if (line == null || !line.getBatchId().equals(batchId)) {
            throw ApiException.notFound("Settlement line");
        }
        if (batch.getStatus() != SettlementBatchStatus.EXPORTED || line.getStatus() != SettlementLineStatus.PENDING) {
            throw ApiException.invalidState("Line is already paid");
        }
        line.setStatus(SettlementLineStatus.PAID);
        line.setPartnerPaymentRef(request.partnerPaymentRef().trim());
        line.setIitWithheldCny(Money.of(request.iitWithheldCny()));
        line.setNetPaidCny(Money.of(request.netPaidCny()));
        line.setPaidAt(request.paidAt());
        lineMapper.updateById(line);
        boolean allPaid = !lineMapper.exists(new LambdaQueryWrapper<SettlementLine>()
                .eq(SettlementLine::getBatchId, batchId)
                .ne(SettlementLine::getStatus, SettlementLineStatus.PAID));
        if (allPaid) {
            batch.setStatus(SettlementBatchStatus.PAID);
            batchMapper.updateById(batch);
        }
        auditService.log(Actor.partner(), AuditAction.PARTNER_LINE_PAID, "settlement_line", lineId,
                Map.of("batchId", batchId, "partnerPaymentRef", line.getPartnerPaymentRef(),
                        "netPaidCny", line.getNetPaidCny(), "batchPaid", allPaid));
        return lineViews(List.of(line)).getFirst();
    }

    /**
     * Settlement lines by contract milestone id, for engineer earnings.
     */
    public Map<Long, SettlementLine> linesByMilestone(Collection<Long> milestoneIds) {
        if (milestoneIds.isEmpty()) {
            return Map.of();
        }
        return lineMapper.selectList(new LambdaQueryWrapper<SettlementLine>()
                        .in(SettlementLine::getContractMilestoneId, milestoneIds))
                .stream().collect(Collectors.toMap(SettlementLine::getContractMilestoneId, Function.identity()));
    }

    // ------------------------------------------------------------------ internals

    private SettlementBatch requireBatch(long batchId) {
        SettlementBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw ApiException.notFound("Settlement batch");
        }
        return batch;
    }

    private SettlementBatchView view(SettlementBatch batch, boolean withLines) {
        List<SettlementLine> lines = lineMapper.selectList(new LambdaQueryWrapper<SettlementLine>()
                .eq(SettlementLine::getBatchId, batch.getId())
                .orderByAsc(SettlementLine::getId));
        return SettlementBatchView.from(batch, lines.size(),
                Money.sum(lines.stream().map(SettlementLine::getTalentAmountUsd).toList()),
                withLines ? lineViews(lines) : null);
    }

    private List<SettlementLineView> lineViews(List<SettlementLine> lines) {
        Map<Long, String> emails = userService.emails(lines.stream().map(SettlementLine::getEngineerUserId).collect(Collectors.toSet()));
        Map<Long, Long> projectByMilestone = milestoneService.refs(lines.stream().map(SettlementLine::getContractMilestoneId).toList())
                .stream().collect(Collectors.toMap(MilestoneRef::milestoneId, MilestoneRef::projectId));
        Map<Long, String> titles = projectService.titles(Set.copyOf(projectByMilestone.values()));
        return lines.stream().map(l -> SettlementLineView.from(l, emails.get(l.getEngineerUserId()),
                titles.get(projectByMilestone.get(l.getContractMilestoneId())))).toList();
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
