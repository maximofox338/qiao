package com.qiao.moderation.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.moderation.service.ModerationService;
import com.qiao.project.domain.ProjectStatus;
import com.qiao.project.service.ProjectService;
import com.qiao.project.web.AdminProjectView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/projects")
@PreAuthorize("hasRole('OPS')")
public class AdminProjectController {

    private final ProjectService projectService;
    private final ModerationService moderationService;

    public AdminProjectController(ProjectService projectService, ModerationService moderationService) {
        this.projectService = projectService;
        this.moderationService = moderationService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminProjectView>> list(@RequestParam(required = false) ProjectStatus status,
                                                          PageQuery pageQuery) {
        return ApiResponse.ok(projectService.adminList(status, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProjectView> get(@PathVariable long id) {
        return ApiResponse.ok(projectService.adminGet(id));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<AdminProjectView> review(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                @Valid @RequestBody ProjectReviewRequest request) {
        return ApiResponse.ok(moderationService.reviewProject(id, request.decision(), request.note(), principal.id()));
    }
}
