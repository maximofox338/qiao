package com.qiao.account.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("jwt_denylist")
public class JwtDenylistEntry extends BaseEntity {
    private String jti;
    private Instant expiresAt;
}
