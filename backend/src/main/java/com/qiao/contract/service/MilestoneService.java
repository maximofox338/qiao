package com.qiao.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.client.domain.ClientCompany;
import com.qiao.client.service.CompanyService;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.util.Texts;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractMember;
import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.domain.ContractMilestoneMapper;
import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.domain.MilestoneStatus;
import com.qiao.contract.web.DisputeView;
import com.qiao.notify.service.MailTemplate;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Milestone state machine: PENDING_FUNDING → FUNDED → SUBMITTED → ACCEPTED | REVISION_REQUESTED (→ SUBMITTED)
 * | DISPUTED → ACCEPTED | REFUNDED. Every transition locks the contract row first so that completion is detected
 * exactly once.
 */
@Service
public class MilestoneService {

    public enum Resolution { ACCEPT, REFUND }

    private final ContractMilestoneMapper milestoneMapper;
    private final ContractService contractService;
    private final ContractParties parties;
    private final ProjectService projectService;
    private final CompanyService companyService;
    private final EngineerProfileService profileService;
    private final ContractNotifier notifier;
    private final AuditService auditService;
    private final Clock clock;

    public MilestoneService(ContractMilestoneMapper milestoneMapper, ContractService contractService,
                            ContractParties parties, ProjectService projectService, CompanyService companyService,
                            EngineerProfileService profileService, ContractNotifier notifier,
                            AuditService auditService, Clock clock) {
        this.milestoneMapper = milestoneMapper;
        this.contractService = contractService;
        this.parties = parties;
        this.projectService = projectService;
        this.companyService = companyService;
        this.profileService = profileService;
        this.notifier = notifier;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Checks that the client may pay this milestone now (contract ACTIVE, milestone PENDING_FUNDING).
     */
    public MilestoneRef requireFundable(long clientUserId, long milestoneId) {
        Locked locked = lockAsClient(clientUserId, milestoneId);
        requireActive(locked.contract());
        if (locked.milestone().getStatus() != MilestoneStatus.PENDING_FUNDING) {
            throw new ApiException(ErrorCode.PAYMENT_ALREADY_PAID, "Milestone is already funded");
        }
        return toRef(locked.milestone(), locked.contract());
    }

    /**
     * PENDING_FUNDING → FUNDED after a successful payment.
     *
     * @return false if the milestone was no longer waiting for funds
     */
    @Transactional
    public boolean markFunded(long milestoneId) {
        Locked locked = lock(milestoneId);
        if (locked.milestone().getStatus() != MilestoneStatus.PENDING_FUNDING) {
            return false;
        }
        update(locked.milestone(), MilestoneStatus.FUNDED);
        notifier.toEngineer(locked.contract(), MailTemplate.MILESTONE_FUNDED, locked.milestone().getTitle(),
                projectTitle(locked.contract()));
        return true;
    }

    /**
     * Only the engineer (or the lead of a team contract) submits; team members get {@code TEAM_LEAD_ONLY}.
     */
    @Transactional
    public ContractMilestone submit(long engineerUserId, long milestoneId, String note) {
        Locked locked = lock(milestoneId);
        if (locked.contract().getEngineerUserId() != engineerUserId) {
            if (parties.isEngineer(locked.contract(), engineerUserId)) {
                throw new ApiException(ErrorCode.TEAM_LEAD_ONLY);
            }
            throw ApiException.notFound("Milestone");
        }
        requireActive(locked.contract());
        ContractMilestone milestone = locked.milestone();
        if (milestone.getStatus() == MilestoneStatus.PENDING_FUNDING) {
            throw new ApiException(ErrorCode.MILESTONE_NOT_FUNDED);
        }
        requireStatus(milestone, EnumSet.of(MilestoneStatus.FUNDED, MilestoneStatus.REVISION_REQUESTED));
        milestone.setSubmittedNote(Texts.trimToNull(note));
        milestone.setSubmittedAt(clock.instant());
        update(milestone, MilestoneStatus.SUBMITTED);
        notifier.toClient(locked.contract(), MailTemplate.MILESTONE_SUBMITTED, milestone.getTitle(), projectTitle(locked.contract()));
        return milestone;
    }

    @Transactional
    public ContractMilestone accept(long clientUserId, long milestoneId) {
        Locked locked = lockAsClient(clientUserId, milestoneId);
        requireActive(locked.contract());
        ContractMilestone milestone = locked.milestone();
        requireStatus(milestone, EnumSet.of(MilestoneStatus.SUBMITTED));
        milestone.setAcceptedAt(clock.instant());
        update(milestone, MilestoneStatus.ACCEPTED);
        notifier.toEngineer(locked.contract(), MailTemplate.MILESTONE_ACCEPTED, milestone.getTitle(), projectTitle(locked.contract()));
        contractService.completeIfFinished(locked.contract());
        return milestone;
    }

    @Transactional
    public ContractMilestone requestRevision(long clientUserId, long milestoneId, String reason) {
        Locked locked = lockAsClient(clientUserId, milestoneId);
        requireActive(locked.contract());
        ContractMilestone milestone = locked.milestone();
        requireStatus(milestone, EnumSet.of(MilestoneStatus.SUBMITTED));
        milestone.setRevisionReason(reason.trim());
        update(milestone, MilestoneStatus.REVISION_REQUESTED);
        notifier.toEngineer(locked.contract(), MailTemplate.MILESTONE_REVISION_REQUESTED, milestone.getTitle(),
                projectTitle(locked.contract()), milestone.getRevisionReason());
        return milestone;
    }

    @Transactional
    public ContractMilestone dispute(long clientUserId, long milestoneId, String reason) {
        Locked locked = lockAsClient(clientUserId, milestoneId);
        requireActive(locked.contract());
        ContractMilestone milestone = locked.milestone();
        requireStatus(milestone, EnumSet.of(MilestoneStatus.SUBMITTED));
        milestone.setDisputeReason(reason.trim());
        update(milestone, MilestoneStatus.DISPUTED);
        return milestone;
    }

    /**
     * Back-office decision on a DISPUTED milestone. Refunding the payment is the caller's job.
     */
    @Transactional
    public ContractMilestone resolveDispute(long milestoneId, Resolution resolution, String note, long adminId) {
        Locked locked = lock(milestoneId);
        ContractMilestone milestone = locked.milestone();
        requireStatus(milestone, EnumSet.of(MilestoneStatus.DISPUTED));
        if (resolution == Resolution.ACCEPT) {
            milestone.setAcceptedAt(clock.instant());
        }
        update(milestone, resolution == Resolution.ACCEPT ? MilestoneStatus.ACCEPTED : MilestoneStatus.REFUNDED);
        auditService.log(Actor.admin(adminId), AuditAction.DISPUTE_RESOLVED, "contract_milestone", milestoneId,
                note == null || note.isBlank() ? Map.of("resolution", resolution.name())
                        : Map.of("resolution", resolution.name(), "note", note.trim()));
        contractService.completeIfFinished(locked.contract());
        return milestone;
    }

    public List<DisputeView> listDisputes() {
        List<ContractMilestone> disputed = milestoneMapper.selectList(new LambdaQueryWrapper<ContractMilestone>()
                .eq(ContractMilestone::getStatus, MilestoneStatus.DISPUTED)
                .orderByAsc(ContractMilestone::getUpdatedAt));
        Map<Long, Contract> contracts = contractService.byIds(disputed.stream().map(ContractMilestone::getContractId).collect(Collectors.toSet()));
        Map<Long, String> titles = projectService.titles(contracts.values().stream().map(Contract::getProjectId).collect(Collectors.toSet()));
        Map<Long, ClientCompany> companies = companyService.byIds(contracts.values().stream().map(Contract::getCompanyId).collect(Collectors.toSet()));
        Map<Long, String> aliases = profileService.aliases(contracts.values().stream().map(Contract::getEngineerUserId).collect(Collectors.toSet()));
        return disputed.stream().map(m -> {
            Contract c = contracts.get(m.getContractId());
            return new DisputeView(m.getId(), c.getId(), c.getProjectId(), titles.get(c.getProjectId()), m.getSeq(),
                    m.getTitle(), m.getDeliverable(), m.getClientAmountUsd(), m.getTalentAmountUsd(),
                    m.getSubmittedNote(), m.getSubmittedAt(), m.getDisputeReason(), c.getCompanyId(),
                    companies.containsKey(c.getCompanyId()) ? companies.get(c.getCompanyId()).getName() : null,
                    aliases.get(c.getEngineerUserId()), m.getUpdatedAt());
        }).toList();
    }

    // ------------------------------------------------------------------ billing read models

    public MilestoneRef ref(long milestoneId) {
        ContractMilestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            throw ApiException.notFound("Milestone");
        }
        return toRef(milestone, contractService.get(milestone.getContractId()));
    }

    public List<MilestoneRef> refs(Collection<Long> milestoneIds) {
        if (milestoneIds.isEmpty()) {
            return List.of();
        }
        return toRefs(milestoneMapper.selectByIds(milestoneIds));
    }

    /**
     * Milestones of the engineer's ACTIVE and COMPLETED contracts (alone or as a team member).
     */
    public List<MilestoneRef> forEngineer(long engineerUserId) {
        Map<Long, Contract> contracts = contractService.listByEngineer(engineerUserId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.COMPLETED)
                .collect(Collectors.toMap(Contract::getId, c -> c));
        if (contracts.isEmpty()) {
            return List.of();
        }
        return toRefs(milestoneMapper.selectList(new LambdaQueryWrapper<ContractMilestone>()
                .in(ContractMilestone::getContractId, contracts.keySet())
                .orderByDesc(ContractMilestone::getContractId)
                .orderByAsc(ContractMilestone::getSeq)));
    }

    /**
     * ACCEPTED milestones without a settlement line, locked until the caller's transaction ends.
     */
    public List<MilestoneRef> lockSettleable() {
        return toRefs(milestoneMapper.selectList(new LambdaQueryWrapper<ContractMilestone>()
                .eq(ContractMilestone::getStatus, MilestoneStatus.ACCEPTED)
                .isNull(ContractMilestone::getSettledLineId)
                .orderByAsc(ContractMilestone::getId)
                .last("FOR UPDATE")));
    }

    public void markSettled(long milestoneId, long settlementLineId) {
        milestoneMapper.update(null, new LambdaUpdateWrapper<ContractMilestone>()
                .set(ContractMilestone::getSettledLineId, settlementLineId)
                .set(ContractMilestone::getUpdatedAt, clock.instant())
                .eq(ContractMilestone::getId, milestoneId)
                .isNull(ContractMilestone::getSettledLineId));
    }

    // ------------------------------------------------------------------ internals

    private record Locked(Contract contract, ContractMilestone milestone) {
    }

    private Locked lock(long milestoneId) {
        ContractMilestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            throw ApiException.notFound("Milestone");
        }
        Contract contract = contractService.lock(milestone.getContractId());
        return new Locked(contract, milestoneMapper.selectByIdForUpdate(milestoneId));
    }

    private Locked lockAsClient(long clientUserId, long milestoneId) {
        Locked locked = lock(milestoneId);
        if (!companyService.isMember(clientUserId, locked.contract().getCompanyId())) {
            throw ApiException.notFound("Milestone");
        }
        return locked;
    }

    private static void requireActive(Contract contract) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONTRACT_NOT_ACTIVE);
        }
    }

    private static void requireStatus(ContractMilestone milestone, Set<MilestoneStatus> allowed) {
        if (!allowed.contains(milestone.getStatus())) {
            throw ApiException.invalidState("Milestone is " + milestone.getStatus());
        }
    }

    private void update(ContractMilestone milestone, MilestoneStatus status) {
        milestone.setStatus(status);
        milestoneMapper.updateById(milestone);
    }

    private String projectTitle(Contract contract) {
        return projectService.get(contract.getProjectId()).getTitle();
    }

    private List<MilestoneRef> toRefs(List<ContractMilestone> milestones) {
        Map<Long, Contract> contracts = contractService.byIds(milestones.stream().map(ContractMilestone::getContractId).collect(Collectors.toSet()));
        Map<Long, List<ContractMember>> members = parties.membersByContract(contracts.values());
        return milestones.stream().map(m -> {
            Contract c = contracts.get(m.getContractId());
            return MilestoneRef.of(m, c, ContractParties.payees(c, members.getOrDefault(c.getId(), List.of()), m.getTalentAmountUsd()));
        }).toList();
    }

    private MilestoneRef toRef(ContractMilestone milestone, Contract contract) {
        return MilestoneRef.of(milestone, contract,
                ContractParties.payees(contract, parties.members(contract), milestone.getTalentAmountUsd()));
    }
}
