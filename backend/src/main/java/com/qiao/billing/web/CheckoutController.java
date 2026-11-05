package com.qiao.billing.web;

import com.qiao.billing.service.PaymentService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private final PaymentService paymentService;

    public CheckoutController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/client/milestones/{id}/checkout")
    public ApiResponse<CheckoutResponse> checkout(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(paymentService.checkout(principal.id(), id));
    }
}
