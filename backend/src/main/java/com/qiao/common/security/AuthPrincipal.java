package com.qiao.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Authenticated caller. Users carry one {@code role} (CLIENT/ENGINEER); admins carry {@code adminRoles}.
 */
public record AuthPrincipal(long id, Kind kind, String role, Set<String> adminRoles, String tokenId,
                            Instant tokenExpiresAt) {

    public enum Kind { USER, ADMIN, PARTNER }

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_PARTNER = "PARTNER";

    public static AuthPrincipal partner() {
        return new AuthPrincipal(0L, Kind.PARTNER, ROLE_PARTNER, Set.of(), null, null);
    }

    public boolean isUser() {
        return kind == Kind.USER;
    }

    public boolean isAdmin() {
        return kind == Kind.ADMIN;
    }

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        switch (kind) {
            case USER -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            case ADMIN -> {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLE_ADMIN));
                adminRoles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            case PARTNER -> authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLE_PARTNER));
        }
        return authorities;
    }
}
