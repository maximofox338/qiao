package com.qiao.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiao.billing.domain.Payment;
import com.qiao.billing.domain.PaymentProviderType;
import com.qiao.common.config.AppProperties;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

/**
 * Stripe Checkout (mode payment, USD). Active when {@code PAYMENT_PROVIDER=stripe}; requires
 * {@code STRIPE_SECRET_KEY} and {@code STRIPE_WEBHOOK_SECRET}.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);
    private static final Set<String> PAID_EVENTS = Set.of("checkout.session.completed",
            "checkout.session.async_payment_succeeded");

    private final RequestOptions requestOptions;
    private final String webhookSecret;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public StripePaymentGateway(PaymentProperties properties, AppProperties appProperties, ObjectMapper objectMapper) {
        Assert.hasText(properties.stripeSecretKey(), "PAYMENT_PROVIDER=stripe requires STRIPE_SECRET_KEY");
        Assert.hasText(properties.stripeWebhookSecret(), "PAYMENT_PROVIDER=stripe requires STRIPE_WEBHOOK_SECRET");
        RequestOptions.RequestOptionsBuilder options = RequestOptions.builder().setApiKey(properties.stripeSecretKey());
        if (properties.stripeApiBase() != null && !properties.stripeApiBase().isBlank()) {
            options.setBaseUrl(properties.stripeApiBase().trim());
        }
        this.requestOptions = options.build();
        this.webhookSecret = properties.stripeWebhookSecret();
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.STRIPE;
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        Payment payment = request.payment();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(appProperties.url("/client/contracts/" + request.contractId() + "?paid=1"))
                .setCancelUrl(appProperties.url("/client/contracts/" + request.contractId()))
                .setClientReferenceId(String.valueOf(payment.getId()))
                .putMetadata("paymentId", String.valueOf(payment.getId()))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(toCents(payment.getAmountUsd()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Milestone " + request.milestoneSeq() + " – " + request.projectTitle())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = Session.create(params, requestOptions);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe checkout creation failed for payment {}", payment.getId(), e);
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public void refund(Payment payment) {
        try {
            Session session = Session.retrieve(payment.getProviderRef(), requestOptions);
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(session.getPaymentIntent())
                    .build(), requestOptions);
        } catch (StripeException e) {
            log.error("Stripe refund failed for payment {}", payment.getId(), e);
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Optional<String> paidCheckoutRef(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException | RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_SIGNATURE);
        }
        if (!PAID_EVENTS.contains(event.getType())) {
            return Optional.empty();
        }
        try {
            JsonNode session = objectMapper.readTree(payload).path("data").path("object");
            return "paid".equals(session.path("payment_status").asText())
                    ? Optional.of(session.path("id").asText())
                    : Optional.empty();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_SIGNATURE);
        }
    }

    private static long toCents(BigDecimal amountUsd) {
        return amountUsd.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
