package com.qiao.billing.web;

import com.qiao.billing.service.PaymentService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentView> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(paymentService.get(principal.id(), id));
    }

    @PostMapping("/fake/{id}/confirm")
    public ApiResponse<PaymentView> confirmFake(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(paymentService.confirmFake(principal.id(), id));
    }

    /**
     * Stripe webhook; authenticated by the {@code Stripe-Signature} header, not by a user token.
     */
    @PostMapping("/stripe/webhook")
    public ApiResponse<Void> stripeWebhook(@RequestBody String payload,
                                           @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        paymentService.handleStripeWebhook(payload, signature);
        return ApiResponse.ok();
    }
}
