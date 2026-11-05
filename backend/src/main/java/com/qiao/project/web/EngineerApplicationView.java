package com.qiao.project.web;

import com.qiao.project.domain.ApplicationStatus;
import com.qiao.project.domain.MilestonePrice;
import com.qiao.project.domain.ProjectStatus;
import com.qiao.team.domain.TeamRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Application as an engineer sees it (talent amounts only). {@code cvShared} is the viewer's own SHARE_FULL_CV for
 * the project. Team applications carry {@code team} (members by alias, with their shares); null otherwise.
 */
public record EngineerApplicationView(Long id, Long projectId, String projectTitle, ProjectStatus projectStatus,
                                      ApplicationStatus status, String coverNote, List<MilestonePrice> prices,
                                      BigDecimal talentTotalUsd, boolean cvShared, Instant createdAt, Team team) {

    public record Team(Long id, String name, TeamRole myRole, BigDecimal mySharePct, List<Member> members) {
    }

    public record Member(String alias, TeamRole role, BigDecimal sharePct) {
    }
}
