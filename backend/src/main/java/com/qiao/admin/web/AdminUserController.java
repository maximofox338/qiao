package com.qiao.admin.web;

import com.qiao.admin.service.AdminUserService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.compliance.service.Actor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/admins")
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<AdminView>> list() {
        return ApiResponse.ok(adminUserService.list());
    }

    @PostMapping
    public ApiResponse<AdminView> create(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody CreateAdminRequest request) {
        return ApiResponse.ok(adminUserService.create(request.email(), request.password(), request.roles(),
                Actor.admin(principal.id())));
    }
}
