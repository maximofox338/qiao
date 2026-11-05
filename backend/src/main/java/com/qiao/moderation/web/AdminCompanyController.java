package com.qiao.moderation.web;

import com.qiao.client.domain.CompanyStatus;
import com.qiao.client.service.CompanyService;
import com.qiao.client.web.AdminCompanyView;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.moderation.service.ModerationService;
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
@RequestMapping("/api/admin/companies")
@PreAuthorize("hasAnyRole('OPS', 'COMPLIANCE')")
public class AdminCompanyController {

    private final CompanyService companyService;
    private final ModerationService moderationService;

    public AdminCompanyController(CompanyService companyService, ModerationService moderationService) {
        this.companyService = companyService;
        this.moderationService = moderationService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminCompanyView>> list(@RequestParam(required = false) CompanyStatus status,
                                                          PageQuery pageQuery) {
        return ApiResponse.ok(companyService.adminList(status, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminCompanyView> get(@PathVariable long id) {
        return ApiResponse.ok(companyService.adminGet(id));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<AdminCompanyView> review(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                @Valid @RequestBody CompanyReviewRequest request) {
        return ApiResponse.ok(moderationService.reviewCompany(id, request.decision(), request.sanctionsStatus(),
                request.note(), principal.id()));
    }
}
