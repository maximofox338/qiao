package com.qiao.moderation.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.web.DisputeView;
import com.qiao.moderation.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('OPS')")
public class AdminDisputeController {

    private final ModerationService moderationService;

    public AdminDisputeController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/disputes")
    public ApiResponse<List<DisputeView>> disputes() {
        return ApiResponse.ok(moderationService.disputes());
    }

    @PostMapping("/milestones/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolve(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                    @Valid @RequestBody DisputeResolutionRequest request) {
        ContractMilestone milestone = moderationService.resolveDispute(id, request.resolution(), request.note(), principal.id());
        return ApiResponse.ok(Map.of("milestoneId", milestone.getId(), "status", milestone.getStatus()));
    }
}
