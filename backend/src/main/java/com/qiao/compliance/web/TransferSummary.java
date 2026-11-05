package com.qiao.compliance.web;

import com.qiao.compliance.service.TransferLevel;

import java.util.Map;

public record TransferSummary(int year, Map<String, Long> uniqueEngineers, Thresholds thresholds, TransferLevel level) {

    public record Thresholds(long warn, long critical, long limit) {
    }
}
