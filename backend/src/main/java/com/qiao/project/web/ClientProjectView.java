package com.qiao.project.web;

import com.qiao.compliance.service.KeywordFlag;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectDeclarations;
import com.qiao.project.domain.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClientProjectView(Long id, String title, String description, String categoryCode, List<String> skills,
                                BigDecimal budgetUsd, LocalDate startDate, LocalDate endDate, ProjectStatus status,
                                List<KeywordFlag> flags, ProjectDeclarations declarations, String reviewNote,
                                Instant reviewedAt, List<ProjectMilestoneView> milestones, long applicationCount,
                                Instant createdAt, Instant updatedAt) {

    public static ClientProjectView from(Project p, List<ProjectMilestoneView> milestones, long applicationCount) {
        return new ClientProjectView(p.getId(), p.getTitle(), p.getDescription(), p.getCategoryCode(),
                p.getSkills() == null ? List.of() : p.getSkills(), p.getBudgetUsd(), p.getStartDate(), p.getEndDate(),
                p.getStatus(), p.getFlags() == null ? List.of() : p.getFlags(), p.getDeclarations(),
                p.getReviewNote(), p.getReviewedAt(), milestones, applicationCount, p.getCreatedAt(), p.getUpdatedAt());
    }
}
