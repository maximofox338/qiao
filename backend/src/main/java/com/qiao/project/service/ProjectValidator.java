package com.qiao.project.service;

import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.money.Money;
import com.qiao.project.web.MilestoneRequest;
import com.qiao.project.web.ProjectRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Structural rules that keep projects from being job postings (spec §1.1).
 */
@Component
public class ProjectValidator {

    static final int MAX_DURATION_MONTHS = 12;

    private final CategoryService categoryService;

    public ProjectValidator(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * @param submitting whether the project is being submitted for review (declarations become mandatory)
     */
    public void validate(ProjectRequest request, boolean submitting) {
        categoryService.requireAllowed(request.categoryCode());
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end == null) {
            throw new ApiException(ErrorCode.END_DATE_REQUIRED);
        }
        if (end.isBefore(start)) {
            throw ApiException.validation("endDate", ValidationCodes.END_BEFORE_START);
        }
        if (end.isAfter(start.plusMonths(MAX_DURATION_MONTHS))) {
            throw new ApiException(ErrorCode.DURATION_TOO_LONG);
        }
        List<MilestoneRequest> milestones = request.milestones();
        if (milestones == null || milestones.isEmpty()) {
            throw new ApiException(ErrorCode.MILESTONES_REQUIRED);
        }
        for (int i = 0; i < milestones.size(); i++) {
            LocalDate due = milestones.get(i).dueDate();
            if (due.isBefore(start) || due.isAfter(end)) {
                throw ApiException.validation("milestones[" + i + "].dueDate", ValidationCodes.DUE_OUTSIDE_RANGE);
            }
        }
        if (Money.sum(milestones.stream().map(MilestoneRequest::amountUsd).toList())
                .compareTo(Money.of(request.budgetUsd())) != 0) {
            throw new ApiException(ErrorCode.MILESTONE_SUM_MISMATCH);
        }
        if (submitting && (request.declarations() == null || !request.declarations().allTrue())) {
            throw new ApiException(ErrorCode.DECLARATIONS_REQUIRED);
        }
    }
}
