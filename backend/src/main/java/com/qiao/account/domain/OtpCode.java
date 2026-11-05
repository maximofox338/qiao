package com.qiao.account.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One-time login code. Only an HMAC of the code is stored.
 */
@Getter
@Setter
@TableName("otp_code")
public class OtpCode extends BaseEntity {

    public static final String PURPOSE_LOGIN = "LOGIN";

    private String email;
    private String codeHash;
    private String purpose;
    private Instant expiresAt;
    private Integer attempts;
    private Instant usedAt;
    private String requestIp;
}
