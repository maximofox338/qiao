package com.qiao.compliance.service;

import com.qiao.common.security.AuthPrincipal;
import com.qiao.compliance.domain.ActorType;

/**
 * Who performed an audited action.
 */
public record Actor(ActorType type, Long id) {

    public static Actor user(long userId) {
        return new Actor(ActorType.USER, userId);
    }

    public static Actor admin(long adminId) {
        return new Actor(ActorType.ADMIN, adminId);
    }

    public static Actor partner() {
        return new Actor(ActorType.PARTNER, null);
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }

    public static Actor of(AuthPrincipal principal) {
        return switch (principal.kind()) {
            case USER -> user(principal.id());
            case ADMIN -> admin(principal.id());
            case PARTNER -> partner();
        };
    }
}
