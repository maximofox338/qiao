package com.qiao.team.service;

import com.qiao.team.domain.Team;
import com.qiao.team.domain.TeamMember;

import java.util.List;

/**
 * An ACTIVE team with its ACTIVE members (lead first), as seen by the project module when the lead applies.
 */
public record ActiveTeam(Team team, List<TeamMember> members) {

    public List<Long> memberUserIds() {
        return members.stream().map(TeamMember::getUserId).toList();
    }
}
