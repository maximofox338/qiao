package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Runs with the {@code prod} profile on top of {@code test} (and SMTP mail): the dev OTP endpoint must not exist,
 * the startup safety check must accept non-public secrets (and a partner key rotation in progress), and OTP codes must
 * never reach the back-office outbox. Contract party names are not set: accepted only because payments are fake.
 */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.mail.mode=smtp",
        "app.partner.api-key=prod-it-partner-key-0123456789abcdef0123",
        "app.partner.api-key-previous=prod-it-previous-partner-key-0123456789"})
class ProductionProfileIT extends IntegrationTest {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void devOtpEndpointDoesNotExistInProd() {
        String email = uniqueEmail("prod");
        post("/api/auth/otp/request", null, Map.of("email", email, "locale", "en")).ok();
        get("/api/dev/last-otp?email=" + email, null).expect(404, "NOT_FOUND");
    }

    @Test
    void otpCodesAreRedactedInTheOutboxReadByAdmins() {
        String email = uniqueEmail("prod");
        post("/api/auth/otp/request", null, Map.of("email", email, "locale", "en")).ok();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        SimpleMailMessage mail = sent.getAllValues().stream()
                .filter(m -> Arrays.asList(m.getTo()).contains(email)).findFirst().orElseThrow();
        Matcher matcher = CODE.matcher(mail.getText());
        assertThat(matcher.find()).isTrue();
        String code = matcher.group(1);

        JsonNode outbox = get("/api/admin/outbox?to=" + email, adminToken()).ok().path("records");
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).path("subject").asText()).isEqualTo("[redacted]");
        assertThat(outbox.get(0).path("body").asText()).isEqualTo("[redacted]");
        assertThat(outbox.toString()).doesNotContain(code);

        // the mailed code still signs the user in
        post("/api/auth/otp/verify", null, Map.of("email", email, "code", code, "role", "CLIENT",
                "acceptTerms", true)).ok();
    }
}
