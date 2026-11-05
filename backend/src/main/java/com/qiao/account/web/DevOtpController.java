package com.qiao.account.web;

import com.qiao.account.service.DevOtpRegistry;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.error.ApiException;
import com.qiao.common.util.Texts;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the last OTP for end-to-end tests. Only registered in the {@code dev} and {@code test} profiles.
 */
@RestController
@Profile({"dev", "test"})
public class DevOtpController {

    private final DevOtpRegistry registry;

    public DevOtpController(DevOtpRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/dev/last-otp")
    public ApiResponse<Map<String, String>> lastOtp(@RequestParam String email) {
        String normalized = Texts.normalizeEmail(email);
        return registry.lastCode(normalized)
                .map(code -> ApiResponse.ok(Map.of("email", normalized, "code", code)))
                .orElseThrow(() -> ApiException.notFound("OTP"));
    }
}
