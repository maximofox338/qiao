package com.qiao.moderation.web;

import com.qiao.client.domain.SanctionsStatus;
import com.qiao.common.review.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompanyReviewRequest(@NotNull ReviewDecision decision, @NotNull SanctionsStatus sanctionsStatus,
                                   @Size(max = 1000) String note) {
}
