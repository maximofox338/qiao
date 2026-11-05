package com.qiao.admin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@TableName("admin_user")
public class AdminUser extends BaseEntity {
    private String email;
    private String passwordHash;
    /**
     * Comma-separated {@link AdminRole} names.
     */
    private String roles;
    /**
     * Base32 TOTP secret; TOTP is required at login when set.
     */
    private String totpSecret;
    private Integer failedAttempts;
    private Instant lockedUntil;
    private AdminStatus status;

    public Set<AdminRole> roleSet() {
        if (roles == null || roles.isBlank()) {
            return EnumSet.noneOf(AdminRole.class);
        }
        return Arrays.stream(roles.split(",")).map(String::trim).map(AdminRole::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AdminRole.class)));
    }

    public void setRoleSet(Set<AdminRole> roleSet) {
        this.roles = roleSet.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
