package com.qiao.project.web;

import com.qiao.profile.web.PseudonymizedProfile;
import com.qiao.project.domain.ApplicationStatus;
import com.qiao.team.domain.TeamRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Application as the client sees it: the engineer only through a pseudonymized profile. A team application adds a
 * team card with every member's pseudonymized profile ({@code engineer} is then the lead); the split is not shown.
 */
public record ClientApplicationView(Long id, Long projectId, ApplicationStatus status, String coverNote,
                                    BigDecimal talentTotalUsd, BigDecimal clientTotalUsd, List<Price> prices,
                                    PseudonymizedProfile engineer, boolean cvAvailable, Instant createdAt,
                                    Team team) {

    public record Price(Long milestoneId, BigDecimal talentAmountUsd, BigDecimal clientAmountUsd) {
    }

    public record Team(String name, String description, List<Member> members) {
    }

    public record Member(String alias, TeamRole role, PseudonymizedProfile profile, boolean cvAvailable) {
    }
}
