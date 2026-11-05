package com.qiao.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.billing.domain.Payment;
import com.qiao.billing.domain.PaymentMapper;
import com.qiao.billing.domain.PaymentProviderType;
import com.qiao.billing.domain.PaymentStatus;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe readiness without real keys: the Stripe provider talks to the official {@code stripe/stripe-mock} image
 * (checkout session creation, refund) and the webhook handler is fed {@code checkout.session.completed} events signed
 * as Stripe documents (HMAC-SHA256 of {@code timestamp + "." + payload} in {@code t=…,v1=…}), plus badly signed ones.
 */
@TestPropertySource(properties = {
        "app.payment.provider=stripe",
        "app.payment.stripe-secret-key=sk_test_123QiaoStripeMock",
        "app.payment.stripe-webhook-secret=" + StripePaymentIT.WEBHOOK_SECRET})
class StripePaymentIT extends IntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_qiao_test_secret";

    @SuppressWarnings("resource")
    static final GenericContainer<?> STRIPE_MOCK = new GenericContainer<>(DockerImageName.parse("stripe/stripe-mock:v0.197.0"))
            .withExposedPorts(12111)
            .waitingFor(Wait.forListeningPort());

    static {
        STRIPE_MOCK.start();
    }

    @DynamicPropertySource
    static void stripeMock(DynamicPropertyRegistry registry) {
        registry.add("app.payment.stripe-api-base",
                () -> "http://" + STRIPE_MOCK.getHost() + ":" + STRIPE_MOCK.getMappedPort(12111));
    }

    @Autowired
    private PaymentMapper paymentMapper;

    @Test
    void checkoutRunsAgainstStripeMockAndASignedWebhookFundsTheMilestone() {
        assertThat(get("/api/public/config", null).ok().path("paymentProvider").asText()).isEqualToIgnoringCase("stripe");
        String client = approvedClient();
        String engineer = approvedEngineer();
        String admin = adminToken();
        long contractId = activeContract(client, engineer);
        List<Long> milestones = new ArrayList<>();
        get("/api/client/contracts/" + contractId, client).ok().path("milestones").forEach(m -> milestones.add(m.path("id").asLong()));
        long milestoneId = milestones.getFirst();

        // ---- checkout session created through the Stripe SDK against stripe-mock
        JsonNode checkout = post("/api/client/milestones/" + milestoneId + "/checkout", client, null).ok();
        long paymentId = checkout.path("paymentId").asLong();
        assertThat(checkout.path("url").asText()).startsWith("https://checkout.stripe.com/");
        Payment payment = paymentMapper.selectById(paymentId);
        assertThat(payment.getProvider()).isEqualTo(PaymentProviderType.STRIPE);
        assertThat(payment.getProviderRef()).startsWith("cs_");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmountUsd()).isEqualByComparingTo("1000.00");
        // fake confirmation is refused with a real provider
        post("/api/payments/fake/" + paymentId + "/confirm", client, null).expect(409, "PAYMENT_PROVIDER_NOT_FAKE");

        String paid = event("evt_paid_1", "checkout.session.completed", payment.getProviderRef(), "paid");

        // ---- badly signed events are refused and change nothing
        webhook(paid, header(paid, "whsec_wrong_secret", now())).expect(400, "INVALID_SIGNATURE");
        webhook(paid, null).expect(400, "INVALID_SIGNATURE");
        webhook(paid, "t=" + now() + ",v1=" + "0".repeat(64)).expect(400, "INVALID_SIGNATURE");
        webhook(paid.replace("\"paid\"", "\"unpaid\""), header(paid, WEBHOOK_SECRET, now())).expect(400, "INVALID_SIGNATURE");
        webhook(paid, header(paid, WEBHOOK_SECRET, now() - 3600)).expect(400, "INVALID_SIGNATURE");
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(milestoneStatus(client, contractId, milestoneId)).isEqualTo("PENDING_FUNDING");

        // ---- a session that is not paid yet (e.g. async payment methods) does not fund anything
        String unpaid = event("evt_unpaid", "checkout.session.completed", payment.getProviderRef(), "unpaid");
        webhook(unpaid, header(unpaid, WEBHOOK_SECRET, now())).ok();
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo(PaymentStatus.PENDING);

        // ---- correctly signed checkout.session.completed → PAID and FUNDED; Stripe retries are idempotent
        webhook(paid, header(paid, WEBHOOK_SECRET, now())).ok();
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(milestoneStatus(client, contractId, milestoneId)).isEqualTo("FUNDED");
        webhook(paid, header(paid, WEBHOOK_SECRET, now())).ok();
        assertThat(paymentMapper.selectCount(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getContractMilestoneId, milestoneId)
                .eq(Payment::getStatus, PaymentStatus.PAID))).isEqualTo(1);
        JsonNode view = get("/api/payments/" + paymentId, client).ok();
        assertThat(view.path("status").asText()).isEqualTo("PAID");
        assertThat(view.path("provider").asText()).isEqualTo("STRIPE");

        // events of other types and unknown sessions are acknowledged and ignored
        String other = event("evt_other", "payment_intent.created", payment.getProviderRef(), "paid");
        webhook(other, header(other, WEBHOOK_SECRET, now())).ok();
        String unknown = event("evt_unknown", "checkout.session.completed", "cs_test_unknown_session", "paid");
        webhook(unknown, header(unknown, WEBHOOK_SECRET, now())).ok();

        // ---- a dispute resolved as REFUND refunds through the Stripe API (session → payment intent → refund)
        post("/api/engineer/milestones/" + milestoneId + "/submit", engineer, Map.of("note", "done")).ok();
        post("/api/client/milestones/" + milestoneId + "/dispute", client, Map.of("reason", "Not what we agreed")).ok();
        post("/api/admin/milestones/" + milestoneId + "/resolve", admin, Map.of("resolution", "REFUND", "note", "refund")).ok();
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(milestoneStatus(client, contractId, milestoneId)).isEqualTo("REFUNDED");
    }

    @Test
    void asyncPaymentSucceededAlsoFundsAndNonAsciiPayloadsVerify() {
        String client = approvedClient();
        String engineer = approvedEngineer();
        long contractId = activeContract(client, engineer);
        long milestoneId = get("/api/client/contracts/" + contractId, client).ok().path("milestones").get(1).path("id").asLong();
        long paymentId = post("/api/client/milestones/" + milestoneId + "/checkout", client, null).ok().path("paymentId").asLong();
        String sessionId = paymentMapper.selectById(paymentId).getProviderRef();

        // the signature covers the raw UTF-8 bytes: a payload with non-ASCII text must still verify
        String paid = event("evt_async", "checkout.session.async_payment_succeeded", sessionId, "paid")
                .replace("\"metadata\":{}", "\"metadata\":{\"note\":\"Hito 2 – 前端看板\"}");
        webhook(paid, header(paid, WEBHOOK_SECRET, now())).ok();
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(milestoneStatus(client, contractId, milestoneId)).isEqualTo("FUNDED");
    }

    // ------------------------------------------------------------------ helpers

    private long activeContract(String client, String engineer) {
        long projectId = openProject(client);
        List<Map<String, Object>> prices = new ArrayList<>();
        get("/api/engineer/projects/" + projectId, engineer).ok().path("milestones").forEach(m -> prices.add(
                Map.of("milestoneId", m.path("id").asLong(), "talentAmountUsd", m.path("talentAmountUsd").decimalValue())));
        long applicationId = post("/api/engineer/projects/" + projectId + "/applications", engineer,
                Map.of("coverNote", "Ready", "prices", prices)).ok().path("id").asLong();
        post("/api/client/applications/" + applicationId + "/select", client, null).ok();
        JsonNode contract = post("/api/client/applications/" + applicationId + "/hire", client, null).ok();
        long contractId = contract.path("id").asLong();
        post("/api/client/contracts/" + contractId + "/documents/" + contract.path("documents").get(0).path("id").asLong()
                + "/accept", client, null).ok();
        long engineerDoc = get("/api/engineer/contracts/" + contractId, engineer).ok().path("documents").get(0).path("id").asLong();
        post("/api/engineer/contracts/" + contractId + "/documents/" + engineerDoc + "/accept", engineer, null).ok();
        return contractId;
    }

    private String milestoneStatus(String client, long contractId, long milestoneId) {
        for (JsonNode m : get("/api/client/contracts/" + contractId, client).ok().path("milestones")) {
            if (m.path("id").asLong() == milestoneId) {
                return m.path("status").asText();
            }
        }
        throw new AssertionError("No milestone " + milestoneId);
    }

    /**
     * A Stripe event envelope with a checkout session as {@code data.object}.
     */
    private static String event(String id, String type, String sessionId, String paymentStatus) {
        return "{\"id\":\"" + id + "\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION
                + "\",\"created\":" + now() + ",\"livemode\":false,\"pending_webhooks\":1,\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"object\":\"checkout.session\",\"mode\":\"payment\","
                + "\"payment_status\":\"" + paymentStatus + "\",\"status\":\"complete\",\"metadata\":{}}}}";
    }

    /**
     * {@code Stripe-Signature} as documented: {@code t=<unix time>,v1=<hex HMAC-SHA256(secret, t + "." + payload)>}.
     */
    private static String header(String payload, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signature);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private Response webhook(String payload, String signature) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/api/payments/stripe/webhook")
                .contentType("application/json; charset=utf-8")
                .content(payload.getBytes(StandardCharsets.UTF_8));
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return send(request, null, null);
    }
}
