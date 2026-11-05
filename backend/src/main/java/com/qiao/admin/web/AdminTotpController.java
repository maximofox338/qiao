package com.qiao.admin.web;

import com.qiao.admin.service.AdminTotpService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service TOTP for any admin; reset of another admin's TOTP for SUPERADMIN.
 */
@RestController
public class AdminTotpController {

    private final AdminTotpService totpService;

    public AdminTotpController(AdminTotpService totpService) {
        this.totpService = totpService;
    }

    @PostMapping("/api/admin/me/totp/setup")
    public ApiResponse<TotpSetupView> setup(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(totpService.setup(principal.id()));
    }

    @PostMapping("/api/admin/me/totp/enable")
    public ApiResponse<AdminLoginResponse.Admin> enable(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody TotpCodeRequest request) {
        return ApiResponse.ok(totpService.enable(principal.id(), request.code()));
    }

    @PostMapping("/api/admin/me/totp/disable")
    public ApiResponse<AdminLoginResponse.Admin> disable(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody TotpCodeRequest request) {
        return ApiResponse.ok(totpService.disable(principal.id(), request.code()));
    }

    @PostMapping("/api/admin/admins/{id}/totp/reset")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<AdminView> reset(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(totpService.reset(principal.id(), id));
    }
}
