package com.qiao.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Rendered contract text; {@code sha256} fingerprints exactly what was accepted.
 */
@Getter
@Setter
@TableName("contract_document")
public class ContractDocument extends BaseEntity {
    private Long contractId;
    private DocumentKind kind;
    /**
     * Owner of an ENGINEER_DELIVERABLES document (one per member in team contracts); null for CLIENT_SERVICES.
     */
    private Long engineerUserId;
    private String templateVersion;
    private String locale;
    private String body;
    private String sha256;
    private Long acceptedByUserId;
    private Instant acceptedAt;
    private String acceptedIp;
}
