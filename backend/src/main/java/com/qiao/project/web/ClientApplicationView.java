package com.qiao.project.web;

import com.qiao.profile.web.PseudonymizedProfile;
import com.qiao.project.domain.ApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Application as the client sees it: the engineer only through a pseudonymized profile.
 */
public record ClientApplicationView(Long id, Long projectId, ApplicationStatus status, String coverNote,
                                    BigDecimal talentTotalUsd, BigDecimal clientTotalUsd, List<Price> prices,
                                    PseudonymizedProfile engineer, boolean cvAvailable, Instant createdAt) {

    public record Price(Long milestoneId, BigDecimal talentAmountUsd, BigDecimal clientAmountUsd) {
    }
}
