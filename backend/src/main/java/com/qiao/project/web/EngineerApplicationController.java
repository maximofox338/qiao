package com.qiao.project.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.compliance.web.ConsentView;
import com.qiao.project.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/engineer/applications")
public class EngineerApplicationController {

    private final ApplicationService applicationService;

    public EngineerApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<List<EngineerApplicationView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(applicationService.listForEngineer(principal.id()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<EngineerApplicationView> withdraw(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable long id) {
        return ApiResponse.ok(applicationService.withdraw(principal.id(), id));
    }

    @PostMapping("/{id}/cv-consent")
    public ApiResponse<ConsentView> cvConsent(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                              @Valid @RequestBody CvConsentRequest request) {
        return ApiResponse.ok(applicationService.cvConsent(principal.id(), id, request.granted()));
    }
}
