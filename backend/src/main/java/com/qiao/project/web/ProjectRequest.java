package com.qiao.project.web;

import com.qiao.project.domain.ProjectDeclarations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Create/update payload. {@code endDate} and {@code milestones} are checked by the service so that the specific
 * codes {@code END_DATE_REQUIRED} and {@code MILESTONES_REQUIRED} are returned.
 */
public record ProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20000) String description,
        @NotBlank @Size(max = 40) String categoryCode,
        @Size(max = 30) List<@NotBlank @Size(max = 40) String> skills,
        @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal budgetUsd,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 20) List<@Valid MilestoneRequest> milestones,
        ProjectDeclarations declarations,
        Boolean submit) {
}
