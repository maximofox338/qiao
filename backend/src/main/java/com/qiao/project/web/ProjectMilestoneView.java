package com.qiao.project.web;

import com.qiao.project.domain.ProjectMilestone;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectMilestoneView(Long id, int seq, String title, String deliverable, LocalDate dueDate,
                                   BigDecimal amountUsd) {

    public static ProjectMilestoneView from(ProjectMilestone m) {
        return new ProjectMilestoneView(m.getId(), m.getSeq(), m.getTitle(), m.getDeliverable(), m.getDueDate(),
                m.getAmountUsd());
    }
}
