package com.qiao.profile.web;

import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.domain.LanguageLevel;
import com.qiao.profile.domain.ProfileStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Back-office view: the full profile, flat, plus the account email and partner reference.
 */
public record AdminEngineerView(Long id, Long userId, String email, String partnerRef, String alias, String headline,
                                String bio, List<String> skills, Integer yearsExperience, String region,
                                LanguageLevel englishLevel, LanguageLevel spanishLevel, BigDecimal hourlyRateUsd,
                                Integer availabilityHoursWeek, String cvText, String education,
                                List<String> portfolioLinks, boolean declarationNoRestrictions, Instant declarationAt,
                                ProfileStatus status, String reviewNote, BigDecimal ratingAvg, int ratingCount,
                                Instant createdAt, Instant updatedAt) {

    public static AdminEngineerView from(EngineerProfile p, String email) {
        return new AdminEngineerView(p.getId(), p.getUserId(), email, p.getPartnerRef(), p.getAlias(), p.getHeadline(),
                p.getBio(), ProfileView.listOrEmpty(p.getSkills()), p.getYearsExperience(), p.getRegion(),
                p.getEnglishLevel(), p.getSpanishLevel(), p.getHourlyRateUsd(), p.getAvailabilityHoursWeek(),
                p.getCvText(), p.getEducation(), ProfileView.listOrEmpty(p.getPortfolioLinks()),
                Boolean.TRUE.equals(p.getDeclarationNoRestrictions()), p.getDeclarationAt(), p.getStatus(),
                p.getReviewNote(), p.getRatingAvg(), p.getRatingCount() == null ? 0 : p.getRatingCount(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
