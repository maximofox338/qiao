package com.qiao.profile.web;

import com.qiao.profile.domain.LanguageLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProfileRequest(
        @Size(max = 160) String headline,
        @Size(max = 5000) String bio,
        @Size(max = 30) List<@NotBlank @Size(max = 40) String> skills,
        @Min(0) @Max(60) Integer yearsExperience,
        @Size(max = 64) String region,
        LanguageLevel englishLevel,
        LanguageLevel spanishLevel,
        @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal hourlyRateUsd,
        @Min(0) @Max(80) Integer availabilityHoursWeek,
        @Size(max = 20000) String cvText,
        @Size(max = 5000) String education,
        @Size(max = 10) List<@NotBlank @Size(max = 255) @Pattern(regexp = "https?://\\S+") String> portfolioLinks,
        Boolean declarationNoRestrictions) {
}
