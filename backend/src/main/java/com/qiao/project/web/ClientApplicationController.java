package com.qiao.project.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.profile.web.CvView;
import com.qiao.project.service.ApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/applications/{id}")
public class ClientApplicationController {

    private final ApplicationService applicationService;

    public ClientApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/shortlist")
    public ApiResponse<ClientApplicationView> shortlist(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(applicationService.shortlist(principal.id(), id));
    }

    @PostMapping("/select")
    public ApiResponse<ClientApplicationView> select(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(applicationService.select(principal.id(), id));
    }

    @PostMapping("/decline")
    public ApiResponse<ClientApplicationView> decline(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(applicationService.decline(principal.id(), id));
    }

    @GetMapping("/cv")
    public ApiResponse<CvView> cv(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                  @RequestParam(required = false) String alias) {
        return ApiResponse.ok(applicationService.cv(principal.id(), id, alias));
    }
}
