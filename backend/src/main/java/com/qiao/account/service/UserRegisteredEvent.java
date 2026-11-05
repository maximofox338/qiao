package com.qiao.account.service;

import com.qiao.account.domain.UserRole;

/**
 * Published synchronously, inside the registration transaction, so modules can create their per-user records.
 */
public record UserRegisteredEvent(long userId, UserRole role) {
}
