package com.qiao.team.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.team.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/engineer")
public class EngineerTeamController {

    private final TeamService teamService;

    public EngineerTeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping("/teams")
    public ApiResponse<TeamView> create(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody TeamRequest request) {
        return ApiResponse.ok(teamService.create(principal.id(), request));
    }

    @GetMapping("/teams")
    public ApiResponse<List<TeamView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(teamService.listForEngineer(principal.id()));
    }

    @GetMapping("/teams/{id}")
    public ApiResponse<TeamView> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(teamService.get(principal.id(), id));
    }

    @PutMapping("/teams/{id}")
    public ApiResponse<TeamView> update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                        @Valid @RequestBody TeamRequest request) {
        return ApiResponse.ok(teamService.update(principal.id(), id, request));
    }

    @PostMapping("/teams/{id}/invitations")
    public ApiResponse<TeamView> invite(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                        @Valid @RequestBody InviteRequest request) {
        return ApiResponse.ok(teamService.invite(principal.id(), id, request.alias()));
    }

    @PostMapping("/teams/{id}/invitations/accept")
    public ApiResponse<TeamView> accept(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(teamService.accept(principal.id(), id));
    }

    @PostMapping("/teams/{id}/invitations/decline")
    public ApiResponse<Void> decline(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        teamService.decline(principal.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/teams/{id}/members/{memberId}/remove")
    public ApiResponse<TeamView> remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                        @PathVariable long memberId) {
        return ApiResponse.ok(teamService.remove(principal.id(), id, memberId));
    }

    @PostMapping("/teams/{id}/leave")
    public ApiResponse<Void> leave(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        teamService.leave(principal.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/teams/{id}/dissolve")
    public ApiResponse<Void> dissolve(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        teamService.dissolve(principal.id(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/team-invitations")
    public ApiResponse<List<InvitationView>> invitations(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(teamService.invitations(principal.id()));
    }
}
