package com.qiao.moderation.web;

import com.qiao.common.review.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectReviewRequest(@NotNull ReviewDecision decision, @Size(max = 1000) String note) {
}
