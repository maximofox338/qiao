package com.qiao.admin.web;

import com.qiao.admin.service.DashboardService;
import com.qiao.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/admin/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }
}
