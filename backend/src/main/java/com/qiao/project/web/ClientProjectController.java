package com.qiao.project.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.project.service.ApplicationService;
import com.qiao.project.service.ProjectService;
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
@RequestMapping("/api/client/projects")
public class ClientProjectController {

    private final ProjectService projectService;
    private final ApplicationService applicationService;

    public ClientProjectController(ProjectService projectService, ApplicationService applicationService) {
        this.projectService = projectService;
        this.applicationService = applicationService;
    }

    @PostMapping
    public ApiResponse<ClientProjectView> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.create(principal.id(), request));
    }

    @GetMapping
    public ApiResponse<List<ClientProjectView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(projectService.listForClient(principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ClientProjectView> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(projectService.getForClient(principal.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ClientProjectView> update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                 @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.update(principal.id(), id, request));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ClientProjectView> submit(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(projectService.submit(principal.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ClientProjectView> cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(projectService.cancel(principal.id(), id));
    }

    @GetMapping("/{id}/applications")
    public ApiResponse<List<ClientApplicationView>> applications(@AuthenticationPrincipal AuthPrincipal principal,
                                                                 @PathVariable long id) {
        return ApiResponse.ok(applicationService.listForProject(principal.id(), id));
    }
}
