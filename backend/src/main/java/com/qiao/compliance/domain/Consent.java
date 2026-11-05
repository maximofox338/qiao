package com.qiao.compliance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only consent record; the current state is the latest row per (user, type, scope).
 */
@Getter
@Setter
@TableName("consent")
public class Consent extends BaseEntity {
    private Long userId;
    private ConsentType type;
    private String version;
    private Boolean granted;
    private String scopeRef;
    private String ip;
    private String userAgent;
}
