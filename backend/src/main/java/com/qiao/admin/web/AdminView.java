package com.qiao.admin.web;

import com.qiao.admin.domain.AdminRole;
import com.qiao.admin.domain.AdminStatus;
import com.qiao.admin.domain.AdminUser;

import java.time.Instant;
import java.util.Set;

public record AdminView(Long id, String email, Set<AdminRole> roles, AdminStatus status, boolean totpEnabled,
                        Instant lockedUntil, Instant createdAt) {

    public static AdminView from(AdminUser a) {
        return new AdminView(a.getId(), a.getEmail(), a.roleSet(), a.getStatus(), a.getTotpSecret() != null,
                a.getLockedUntil(), a.getCreatedAt());
    }
}
