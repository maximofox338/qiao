package com.qiao.project.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Project as engineers see it: talent-side amounts only, client identified by country.
 */
public record EngineerProjectView(Long id, String title, String description, String categoryCode,
                                  List<String> skills, String clientCountry, BigDecimal talentBudgetUsd,
                                  LocalDate startDate, LocalDate endDate, List<Milestone> milestones,
                                  boolean applied) {

    public record Milestone(Long id, int seq, String title, String deliverable, LocalDate dueDate,
                            BigDecimal talentAmountUsd) {
    }
}
