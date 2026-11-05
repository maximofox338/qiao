package com.qiao.account.service;

import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.security.TokenAcceptancePolicy;
import org.springframework.stereotype.Component;

/**
 * Rejects user tokens once the account is locked or deleted.
 */
@Component
public class UserStatusPolicy implements TokenAcceptancePolicy {

    private final UserService userService;

    public UserStatusPolicy(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean accepts(AuthPrincipal principal) {
        return !principal.isUser() || userService.isActive(principal.id());
    }
}
