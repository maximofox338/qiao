package com.qiao.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@TableName("contract")
public class Contract extends BaseEntity {
    private Long projectId;
    private Long applicationId;
    private Long companyId;
    private Long engineerUserId;
    private ContractStatus status;
    private BigDecimal feePct;
    private BigDecimal talentTotalUsd;
    private BigDecimal clientTotalUsd;
    private Instant activatedAt;
    private Instant completedAt;
}
