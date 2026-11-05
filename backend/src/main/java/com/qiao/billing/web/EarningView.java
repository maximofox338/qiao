package com.qiao.billing.web;

import com.qiao.contract.domain.MilestoneStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record EarningView(Long contractMilestoneId, Long contractId, String projectTitle, int seq,
                          String milestoneTitle, BigDecimal talentAmountUsd, MilestoneStatus milestoneStatus,
                          SettlementStatus settlementStatus, Instant paidAt) {

    public enum SettlementStatus { NONE, PENDING, PAID }
}
