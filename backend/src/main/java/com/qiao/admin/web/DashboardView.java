package com.qiao.admin.web;

import com.qiao.compliance.service.TransferLevel;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardView(ByStatus companies, ByStatus engineers, ByStatus projects, ByStatus contracts,
                            BigDecimal gmvUsd, BigDecimal feesUsd, Transfers transfers) {

    public record ByStatus(Map<String, Long> byStatus) {
    }

    public record Transfers(int year, long uniqueEngineers, TransferLevel level) {
    }
}
