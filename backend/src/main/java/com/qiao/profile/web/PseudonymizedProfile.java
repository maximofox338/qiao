package com.qiao.profile.web;

import com.qiao.profile.domain.EngineerProfile;
import com.qiao.profile.domain.LanguageLevel;

import java.math.BigDecimal;
import java.util.List;

/**
 * What clients may see about an engineer (spec §1.5): no email, real name, CV, education or portfolio.
 */
public record PseudonymizedProfile(String alias, String headline, List<String> skills, Integer yearsExperience,
                                   String region, LanguageLevel englishLevel, LanguageLevel spanishLevel,
                                   BigDecimal hourlyRateUsd, Integer availabilityHoursWeek, BigDecimal ratingAvg,
                                   int ratingCount) {

    public static PseudonymizedProfile from(EngineerProfile p) {
        return new PseudonymizedProfile(p.getAlias(), p.getHeadline(), ProfileView.listOrEmpty(p.getSkills()),
                p.getYearsExperience(), p.getRegion(), p.getEnglishLevel(), p.getSpanishLevel(), p.getHourlyRateUsd(),
                p.getAvailabilityHoursWeek(), p.getRatingAvg(), p.getRatingCount() == null ? 0 : p.getRatingCount());
    }
}
