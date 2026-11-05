package com.qiao.contract.web;

import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.domain.MilestoneStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ClientMilestoneView(Long id, int seq, String title, String deliverable, LocalDate dueDate,
                                  BigDecimal clientAmountUsd, MilestoneStatus status, String submittedNote,
                                  Instant submittedAt, Instant acceptedAt, String revisionReason,
                                  String disputeReason) {

    public static ClientMilestoneView from(ContractMilestone m) {
        return new ClientMilestoneView(m.getId(), m.getSeq(), m.getTitle(), m.getDeliverable(), m.getDueDate(),
                m.getClientAmountUsd(), m.getStatus(), m.getSubmittedNote(), m.getSubmittedAt(), m.getAcceptedAt(),
                m.getRevisionReason(), m.getDisputeReason());
    }
}
