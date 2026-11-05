package com.qiao.billing.web;

import com.qiao.billing.service.EarningsService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EngineerEarningsController {

    private final EarningsService earningsService;

    public EngineerEarningsController(EarningsService earningsService) {
        this.earningsService = earningsService;
    }

    @GetMapping("/api/engineer/earnings")
    public ApiResponse<List<EarningView>> earnings(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(earningsService.forEngineer(principal.id()));
    }
}
