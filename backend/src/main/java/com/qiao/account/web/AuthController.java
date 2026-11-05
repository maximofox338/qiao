package com.qiao.account.web;

import com.qiao.account.service.AuthService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    public ApiResponse<Map<String, Boolean>> requestCode(@Valid @RequestBody OtpRequest request) {
        authService.requestCode(request.email(), request.locale());
        return ApiResponse.ok(Map.of("sent", true));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<LoginResponse> verify(@Valid @RequestBody OtpVerifyRequest request) {
        return ApiResponse.ok(authService.verify(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal);
        return ApiResponse.ok();
    }
}
