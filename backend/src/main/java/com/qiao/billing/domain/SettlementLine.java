package com.qiao.billing.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@TableName("settlement_line")
public class SettlementLine extends BaseEntity {
    private Long batchId;
    private Long contractMilestoneId;
    private Long engineerUserId;
    private String partnerRef;
    private String alias;
    private BigDecimal talentAmountUsd;
    private SettlementLineStatus status;
    private String partnerPaymentRef;
    private BigDecimal iitWithheldCny;
    private BigDecimal netPaidCny;
    private Instant paidAt;
}
