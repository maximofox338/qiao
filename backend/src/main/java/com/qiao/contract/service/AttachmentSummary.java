package com.qiao.contract.service;

import java.time.Instant;

/**
 * File shared in a contract; {@code milestoneId} is null for contract-level files.
 */
public record AttachmentSummary(Long id, Long milestoneId, Long uploaderUserId, String filename, String contentType,
                                long sizeBytes, Instant createdAt) {
}
