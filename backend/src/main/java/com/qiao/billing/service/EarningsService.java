package com.qiao.billing.service;

import com.qiao.billing.domain.SettlementLine;
import com.qiao.billing.domain.SettlementLineStatus;
import com.qiao.billing.web.EarningView;
import com.qiao.contract.service.MilestoneRef;
import com.qiao.contract.service.MilestoneService;
import com.qiao.project.service.ProjectService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EarningsService {

    private final MilestoneService milestoneService;
    private final SettlementService settlementService;
    private final ProjectService projectService;

    public EarningsService(MilestoneService milestoneService, SettlementService settlementService,
                           ProjectService projectService) {
        this.milestoneService = milestoneService;
        this.settlementService = settlementService;
        this.projectService = projectService;
    }

    public List<EarningView> forEngineer(long engineerUserId) {
        List<MilestoneRef> milestones = milestoneService.forEngineer(engineerUserId);
        Map<Long, SettlementLine> lines = settlementService.linesByMilestone(
                milestones.stream().map(MilestoneRef::milestoneId).toList());
        Map<Long, String> titles = projectService.titles(
                milestones.stream().map(MilestoneRef::projectId).collect(Collectors.toSet()));
        return milestones.stream().map(m -> {
            SettlementLine line = lines.get(m.milestoneId());
            EarningView.SettlementStatus settlement = line == null ? EarningView.SettlementStatus.NONE
                    : line.getStatus() == SettlementLineStatus.PAID ? EarningView.SettlementStatus.PAID
                    : EarningView.SettlementStatus.PENDING;
            return new EarningView(m.milestoneId(), m.contractId(), titles.get(m.projectId()), m.seq(), m.title(),
                    m.talentAmountUsd(), m.status(), settlement, line == null ? null : line.getPaidAt());
        }).toList();
    }
}
