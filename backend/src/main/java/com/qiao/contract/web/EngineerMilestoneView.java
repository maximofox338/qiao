package com.qiao.contract.web;

import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.domain.MilestoneStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code talentAmountUsd} is the milestone's talent amount (the whole team's in a team contract);
 * {@code myAmountUsd} is the viewer's part of it.
 */
public record EngineerMilestoneView(Long id, int seq, String title, String deliverable, LocalDate dueDate,
                                    BigDecimal talentAmountUsd, MilestoneStatus status, String submittedNote,
                                    Instant submittedAt, Instant acceptedAt, String revisionReason,
                                    String disputeReason, BigDecimal myAmountUsd) {

    public static EngineerMilestoneView from(ContractMilestone m) {
        return from(m, m.getTalentAmountUsd());
    }

    public static EngineerMilestoneView from(ContractMilestone m, BigDecimal myAmountUsd) {
        return new EngineerMilestoneView(m.getId(), m.getSeq(), m.getTitle(), m.getDeliverable(), m.getDueDate(),
                m.getTalentAmountUsd(), m.getStatus(), m.getSubmittedNote(), m.getSubmittedAt(), m.getAcceptedAt(),
                m.getRevisionReason(), m.getDisputeReason(), myAmountUsd);
    }
}
