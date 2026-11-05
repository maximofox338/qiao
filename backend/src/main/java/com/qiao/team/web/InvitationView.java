package com.qiao.team.web;

import java.time.Instant;
import java.util.List;

public record InvitationView(Long teamId, String teamName, String description, String leadAlias,
                             List<String> memberAliases, Instant invitedAt) {
}
