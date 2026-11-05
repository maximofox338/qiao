package com.qiao.billing.service;

import com.qiao.billing.domain.PaymentProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code PAYMENT_PROVIDER=fake|stripe}, {@code STRIPE_SECRET_KEY}, {@code STRIPE_WEBHOOK_SECRET} and
 * {@code STRIPE_API_BASE} (empty = Stripe's API; set only to test against {@code stripe/stripe-mock}).
 */
@ConfigurationProperties("app.payment")
public record PaymentProperties(
        @DefaultValue("fake") PaymentProviderType provider,
        String stripeSecretKey,
        String stripeWebhookSecret,
        String stripeApiBase) {
}
