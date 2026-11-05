package com.qiao.project.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.project.service.ApplicationService;
import com.qiao.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineer/projects")
public class EngineerProjectController {

    private final ProjectService projectService;
    private final ApplicationService applicationService;

    public EngineerProjectController(ProjectService projectService, ApplicationService applicationService) {
        this.projectService = projectService;
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<PageResult<EngineerProjectView>> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(required = false) String category,
                                                             PageQuery pageQuery) {
        return ApiResponse.ok(projectService.listOpen(principal.id(), q, category, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<EngineerProjectView> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(projectService.getForEngineer(principal.id(), id));
    }

    @PostMapping("/{id}/applications")
    public ApiResponse<EngineerApplicationView> apply(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable long id, @Valid @RequestBody ApplyRequest request) {
        return ApiResponse.ok(applicationService.apply(principal.id(), id, request));
    }
}
