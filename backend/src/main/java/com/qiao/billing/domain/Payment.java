package com.qiao.billing.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Client prepayment of one contract milestone, collected by the Hong Kong Ltd.
 */
@Getter
@Setter
@TableName("payment")
public class Payment extends BaseEntity {
    private Long contractMilestoneId;
    private PaymentProviderType provider;
    private String providerRef;
    private BigDecimal amountUsd;
    private PaymentStatus status;
    private Instant paidAt;
}
