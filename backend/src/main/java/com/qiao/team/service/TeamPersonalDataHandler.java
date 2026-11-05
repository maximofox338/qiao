package com.qiao.team.service;

import com.qiao.account.service.PersonalDataHandler;
import org.springframework.stereotype.Component;

/**
 * Teams are exported; on deletion the user's teams are dissolved and other memberships end.
 */
@Component
public class TeamPersonalDataHandler implements PersonalDataHandler {

    private final TeamService teamService;

    public TeamPersonalDataHandler(TeamService teamService) {
        this.teamService = teamService;
    }

    @Override
    public String section() {
        return "teams";
    }

    @Override
    public Object export(long userId) {
        return teamService.exportFor(userId);
    }

    @Override
    public void anonymize(long userId) {
        teamService.anonymize(userId);
    }
}
