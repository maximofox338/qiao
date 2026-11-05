package com.qiao.team.web;

import com.qiao.team.domain.TeamMemberStatus;
import com.qiao.team.domain.TeamRole;
import com.qiao.team.domain.TeamStatus;

import java.time.Instant;
import java.util.List;

/**
 * A team as its members see it. Members are identified by alias, headline and skills only.
 */
public record TeamView(Long id, String name, String description, TeamStatus status, String leadAlias,
                       TeamRole myRole, TeamMemberStatus myStatus, List<Member> members, int activeMembers,
                       boolean canApply, Instant createdAt) {

    public record Member(Long id, String alias, String headline, List<String> skills, TeamRole role,
                         TeamMemberStatus status) {
    }
}
