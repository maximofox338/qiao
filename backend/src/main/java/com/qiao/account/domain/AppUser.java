package com.qiao.account.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("app_user")
public class AppUser extends BaseEntity {
    private String email;
    private UserRole role;
    private UserStatus status;
    private String locale;
    private Instant otpLockedUntil;
    private Instant lastLoginAt;
}
