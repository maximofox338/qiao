package com.qiao.contract.service;

import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.domain.MilestoneStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model of a contract milestone with its contract keys, for billing and settlement.
 */
public record MilestoneRef(Long milestoneId, Long contractId, Long projectId, Long companyId, Long engineerUserId,
                           int seq, String title, BigDecimal talentAmountUsd, BigDecimal clientAmountUsd,
                           MilestoneStatus status, Instant acceptedAt, Long settledLineId) {

    static MilestoneRef of(ContractMilestone m, Contract c) {
        return new MilestoneRef(m.getId(), c.getId(), c.getProjectId(), c.getCompanyId(), c.getEngineerUserId(),
                m.getSeq(), m.getTitle(), m.getTalentAmountUsd(), m.getClientAmountUsd(), m.getStatus(),
                m.getAcceptedAt(), m.getSettledLineId());
    }
}
