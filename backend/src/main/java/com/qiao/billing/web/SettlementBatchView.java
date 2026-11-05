package com.qiao.billing.web;

import com.qiao.billing.domain.SettlementBatch;
import com.qiao.billing.domain.SettlementBatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code lines} is only filled in detail views.
 */
public record SettlementBatchView(Long id, String period, SettlementBatchStatus status, Long createdByAdminId,
                                  Instant exportedAt, Instant createdAt, int lineCount, BigDecimal totalTalentUsd,
                                  List<SettlementLineView> lines) {

    public static SettlementBatchView from(SettlementBatch b, int lineCount, BigDecimal total, List<SettlementLineView> lines) {
        return new SettlementBatchView(b.getId(), b.getPeriod(), b.getStatus(), b.getCreatedByAdminId(),
                b.getExportedAt(), b.getCreatedAt(), lineCount, total, lines);
    }
}
