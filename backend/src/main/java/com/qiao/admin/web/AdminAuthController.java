package com.qiao.admin.web;

import com.qiao.admin.domain.AdminUser;
import com.qiao.admin.service.AdminAuthService;
import com.qiao.admin.service.AdminUserService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminUserService adminUserService;

    public AdminAuthController(AdminAuthService authService, AdminUserService adminUserService) {
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(authService.login(request.email(), request.password(), request.totp()));
    }

    @GetMapping("/me")
    public ApiResponse<AdminLoginResponse.Admin> me(@AuthenticationPrincipal AuthPrincipal principal) {
        AdminUser admin = adminUserService.get(principal.id());
        return ApiResponse.ok(AdminLoginResponse.Admin.from(admin));
    }

    /**
     * Any admin: changes the own password and ends every other session; returns a new token for this one.
     */
    @PostMapping("/me/password")
    public ApiResponse<AdminLoginResponse> changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changePassword(principal.id(), request.currentPassword(),
                request.newPassword()));
    }
}
