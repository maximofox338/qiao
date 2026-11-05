package com.qiao.admin.service;

import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.security.TokenAcceptancePolicy;
import org.springframework.stereotype.Component;

/**
 * Rejects admin tokens once the admin is disabled.
 */
@Component
public class AdminStatusPolicy implements TokenAcceptancePolicy {

    private final AdminUserService adminUserService;

    public AdminStatusPolicy(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Override
    public boolean accepts(AuthPrincipal principal) {
        return !principal.isAdmin() || adminUserService.isActive(principal.id());
    }
}
