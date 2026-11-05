package com.qiao.review.web;

import com.qiao.account.domain.UserRole;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.contract.service.ReviewSummary;
import com.qiao.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/client/contracts/{id}/review")
    public ApiResponse<ReviewSummary> clientReview(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                   @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewService.reviewAsClient(principal.id(), id, request));
    }

    @PostMapping("/api/engineer/contracts/{id}/review")
    public ApiResponse<ReviewSummary> engineerReview(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                                     @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewService.reviewAsEngineer(principal.id(), id, request));
    }

    @GetMapping("/api/admin/reviews")
    @PreAuthorize("hasAnyRole('OPS', 'COMPLIANCE')")
    public ApiResponse<PageResult<AdminReviewView>> adminList(@RequestParam(required = false) UserRole side,
                                                              PageQuery pageQuery) {
        return ApiResponse.ok(reviewService.adminList(side, pageQuery));
    }
}
