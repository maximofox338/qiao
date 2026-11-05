package com.qiao.delivery.web;

import com.qiao.account.domain.UserRole;

import java.time.Instant;

/**
 * {@code senderAlias} is the engineer alias or the client company name, never a person's name or email.
 */
public record MessageView(Long id, Long contractId, Long senderUserId, UserRole senderSide, String senderAlias,
                          String body, boolean flagged, Instant createdAt) {
}
