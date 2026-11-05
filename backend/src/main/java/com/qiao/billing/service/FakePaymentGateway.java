package com.qiao.billing.service;

import com.qiao.billing.domain.Payment;
import com.qiao.billing.domain.PaymentProviderType;
import com.qiao.common.config.AppProperties;
import com.qiao.common.error.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Development/staging provider: the web app shows {@code /pay/fake/{paymentId}} and confirms through
 * {@code POST /api/payments/fake/{paymentId}/confirm}.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    private final AppProperties appProperties;

    public FakePaymentGateway(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.FAKE;
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        long paymentId = request.payment().getId();
        return new CheckoutSession("fake_" + paymentId, appProperties.url("/pay/fake/" + paymentId));
    }

    @Override
    public void refund(Payment payment) {
        // nothing to call: fake payments move no money
    }

    @Override
    public Optional<String> paidCheckoutRef(String payload, String signatureHeader) {
        throw ApiException.notFound("Webhook");
    }
}
