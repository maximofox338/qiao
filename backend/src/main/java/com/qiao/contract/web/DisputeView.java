package com.qiao.contract.web;

import java.math.BigDecimal;
import java.time.Instant;

public record DisputeView(Long milestoneId, Long contractId, Long projectId, String projectTitle, int seq,
                          String title, String deliverable, BigDecimal clientAmountUsd, BigDecimal talentAmountUsd,
                          String submittedNote, Instant submittedAt, String disputeReason, Long companyId,
                          String companyName, String engineerAlias, Instant updatedAt) {
}
