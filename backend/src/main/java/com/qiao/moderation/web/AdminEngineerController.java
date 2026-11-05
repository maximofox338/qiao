package com.qiao.moderation.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.moderation.service.ModerationService;
import com.qiao.profile.domain.ProfileStatus;
import com.qiao.profile.service.EngineerProfileService;
import com.qiao.profile.web.AdminEngineerView;
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

/**
 * Engineer profiles are addressed by profile id.
 */
@RestController
@RequestMapping("/api/admin/engineers")
@PreAuthorize("hasRole('OPS')")
public class AdminEngineerController {

    private final EngineerProfileService profileService;
    private final ModerationService moderationService;

    public AdminEngineerController(EngineerProfileService profileService, ModerationService moderationService) {
        this.profileService = profileService;
        this.moderationService = moderationService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminEngineerView>> list(@RequestParam(required = false) ProfileStatus status,
                                                           PageQuery pageQuery) {
        return ApiResponse.ok(profileService.adminList(status, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminEngineerView> get(@PathVariable long id) {
        return ApiResponse.ok(profileService.adminGet(id));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<AdminEngineerView> review(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                 @Valid @RequestBody EngineerReviewRequest request) {
        return ApiResponse.ok(moderationService.reviewEngineer(id, request.decision(), request.note(),
                request.partnerRef(), principal.id()));
    }
}
