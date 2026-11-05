package com.qiao.admin.service;

import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.security.TokenAcceptancePolicy;
import org.springframework.stereotype.Component;

/**
 * Rejects admin tokens once the admin is disabled or the admin's token version moved on (password changed, disabled
 * and enabled again): the token's {@code tv} claim must equal {@code admin_user.token_version}.
 */
@Component
public class AdminStatusPolicy implements TokenAcceptancePolicy {

    private final AdminUserService adminUserService;

    public AdminStatusPolicy(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Override
    public boolean accepts(AuthPrincipal principal) {
        return !principal.isAdmin() || adminUserService.acceptsToken(principal.id(), principal.tokenVersion());
    }
}
