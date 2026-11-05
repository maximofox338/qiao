package com.qiao.billing.web;

import com.qiao.billing.domain.SettlementLine;
import com.qiao.billing.domain.SettlementLineStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementLineView(Long lineId, String alias, String partnerRef, String engineerEmail,
                                 BigDecimal talentAmountUsd, Long contractMilestoneId, String projectTitle,
                                 SettlementLineStatus status, String partnerPaymentRef, BigDecimal iitWithheldCny,
                                 BigDecimal netPaidCny, Instant paidAt) {

    public static SettlementLineView from(SettlementLine l, String engineerEmail, String projectTitle) {
        return new SettlementLineView(l.getId(), l.getAlias(), l.getPartnerRef(), engineerEmail, l.getTalentAmountUsd(),
                l.getContractMilestoneId(), projectTitle, l.getStatus(), l.getPartnerPaymentRef(),
                l.getIitWithheldCny(), l.getNetPaidCny(), l.getPaidAt());
    }
}
