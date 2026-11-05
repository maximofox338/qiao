package com.qiao.profile.web;

import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.domain.LanguageLevel;
import com.qiao.profile.domain.ProfileStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The engineer's own full profile.
 */
public record ProfileView(Long id, String alias, String headline, String bio, List<String> skills,
                          Integer yearsExperience, String region, LanguageLevel englishLevel,
                          LanguageLevel spanishLevel, BigDecimal hourlyRateUsd, Integer availabilityHoursWeek,
                          String cvText, String education, List<String> portfolioLinks,
                          boolean declarationNoRestrictions, Instant declarationAt, ProfileStatus status,
                          String reviewNote, BigDecimal ratingAvg, int ratingCount) {

    public static ProfileView from(EngineerProfile p) {
        return new ProfileView(p.getId(), p.getAlias(), p.getHeadline(), p.getBio(), listOrEmpty(p.getSkills()),
                p.getYearsExperience(), p.getRegion(), p.getEnglishLevel(), p.getSpanishLevel(), p.getHourlyRateUsd(),
                p.getAvailabilityHoursWeek(), p.getCvText(), p.getEducation(), listOrEmpty(p.getPortfolioLinks()),
                Boolean.TRUE.equals(p.getDeclarationNoRestrictions()), p.getDeclarationAt(), p.getStatus(),
                p.getReviewNote(), p.getRatingAvg(), p.getRatingCount() == null ? 0 : p.getRatingCount());
    }

    public static List<String> listOrEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
