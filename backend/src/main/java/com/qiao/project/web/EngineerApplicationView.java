package com.qiao.project.web;

import com.qiao.project.domain.ApplicationStatus;
import com.qiao.project.domain.MilestonePrice;
import com.qiao.project.domain.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EngineerApplicationView(Long id, Long projectId, String projectTitle, ProjectStatus projectStatus,
                                      ApplicationStatus status, String coverNote, List<MilestonePrice> prices,
                                      BigDecimal talentTotalUsd, boolean cvShared, Instant createdAt) {
}
