package com.qiao.review.web;

import com.qiao.account.domain.UserRole;

import java.time.Instant;

/**
 * Review as the back-office sees it: who wrote it about whom (engineer alias or company name).
 */
public record AdminReviewView(Long id, Long contractId, Long projectId, String projectTitle, UserRole fromSide,
                              String fromLabel, String toLabel, int stars, String comment, Instant createdAt) {
}
