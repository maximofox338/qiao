package com.qiao.profile.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.profile.service.EngineerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineer/profile")
public class EngineerProfileController {

    private final EngineerProfileService profileService;

    public EngineerProfileController(EngineerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<ProfileView> get(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(profileService.getOwn(principal.id()));
    }

    @PutMapping
    public ApiResponse<ProfileView> update(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok(profileService.update(principal.id(), request));
    }

    @PostMapping("/submit")
    public ApiResponse<ProfileView> submit(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(profileService.submit(principal.id()));
    }
}
