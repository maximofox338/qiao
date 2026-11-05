package com.qiao.project.web;

import com.qiao.client.domain.ClientCompany;
import com.qiao.client.domain.CompanyStatus;
import com.qiao.compliance.service.KeywordFlag;
import com.qiao.project.domain.Project;
import com.qiao.project.domain.ProjectDeclarations;
import com.qiao.project.domain.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminProjectView(Long id, Long companyId, String companyName, String companyCountry,
                               CompanyStatus companyStatus, String title, String description, String categoryCode,
                               List<String> skills, BigDecimal budgetUsd, LocalDate startDate, LocalDate endDate,
                               ProjectStatus status, List<KeywordFlag> flags, ProjectDeclarations declarations,
                               String reviewNote, Long reviewedBy, Instant reviewedAt,
                               List<ProjectMilestoneView> milestones, Instant createdAt, Instant updatedAt) {

    public static AdminProjectView from(Project p, ClientCompany company, List<ProjectMilestoneView> milestones) {
        return new AdminProjectView(p.getId(), p.getCompanyId(), company == null ? null : company.getName(),
                company == null ? null : company.getCountry(), company == null ? null : company.getStatus(),
                p.getTitle(), p.getDescription(), p.getCategoryCode(), p.getSkills() == null ? List.of() : p.getSkills(),
                p.getBudgetUsd(), p.getStartDate(), p.getEndDate(), p.getStatus(),
                p.getFlags() == null ? List.of() : p.getFlags(), p.getDeclarations(), p.getReviewNote(),
                p.getReviewedBy(), p.getReviewedAt(), milestones, p.getCreatedAt(), p.getUpdatedAt());
    }
}
