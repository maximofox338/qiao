package com.qiao.billing.web;

import com.qiao.billing.domain.Payment;
import com.qiao.billing.domain.PaymentProviderType;
import com.qiao.billing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentView(Long id, Long contractId, Long contractMilestoneId, PaymentProviderType provider,
                          PaymentStatus status, BigDecimal amountUsd, Instant paidAt) {

    public static PaymentView from(Payment p, long contractId) {
        return new PaymentView(p.getId(), contractId, p.getContractMilestoneId(), p.getProvider(), p.getStatus(),
                p.getAmountUsd(), p.getPaidAt());
    }
}
