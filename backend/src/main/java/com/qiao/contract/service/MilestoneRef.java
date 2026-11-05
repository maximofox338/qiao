package com.qiao.contract.service;

import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.domain.MilestoneStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read model of a contract milestone with its contract keys, for billing and settlement. {@code engineerUserId} is
 * the engineer or team lead; {@code payees} says who receives which part of {@code talentAmountUsd}.
 */
public record MilestoneRef(Long milestoneId, Long contractId, Long projectId, Long companyId, Long engineerUserId,
                           int seq, String title, BigDecimal talentAmountUsd, BigDecimal clientAmountUsd,
                           MilestoneStatus status, Instant acceptedAt, Long settledLineId, List<Payee> payees) {

    static MilestoneRef of(ContractMilestone m, Contract c, List<Payee> payees) {
        return new MilestoneRef(m.getId(), c.getId(), c.getProjectId(), c.getCompanyId(), c.getEngineerUserId(),
                m.getSeq(), m.getTitle(), m.getTalentAmountUsd(), m.getClientAmountUsd(), m.getStatus(),
                m.getAcceptedAt(), m.getSettledLineId(), payees);
    }

    /**
     * The part of this milestone paid to {@code engineerUserId}, or null if they are not a payee.
     */
    public Payee payee(long engineerUserId) {
        return payees.stream().filter(p -> p.engineerUserId() == engineerUserId).findFirst().orElse(null);
    }
}
