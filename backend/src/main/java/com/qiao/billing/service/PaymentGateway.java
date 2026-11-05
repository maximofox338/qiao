package com.qiao.billing.service;

import com.qiao.billing.domain.Payment;
import com.qiao.billing.domain.PaymentProviderType;

import java.util.Optional;

/**
 * Payment provider adapter. Exactly one implementation is active, chosen by {@code PAYMENT_PROVIDER}.
 */
public interface PaymentGateway {

    PaymentProviderType type();

    CheckoutSession createCheckout(CheckoutRequest request);

    void refund(Payment payment);

    /**
     * Verifies a provider webhook and returns the provider reference of a completed, paid checkout.
     */
    Optional<String> paidCheckoutRef(String payload, String signatureHeader);

    record CheckoutRequest(Payment payment, long contractId, int milestoneSeq, String projectTitle) {
    }

    record CheckoutSession(String providerRef, String url) {
    }
}
