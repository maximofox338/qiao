package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIT extends IntegrationTest {

    @Test
    void clientRegistersWithOtpThenLogsInAgain() {
        String email = uniqueEmail("client");
        String code = requestCode(email);

        post("/api/auth/otp/verify", null, Map.of("email", email, "code", code))
                .expect(400, "AUTH_ROLE_REQUIRED");
        post("/api/auth/otp/verify", null, Map.of("email", email, "code", code, "role", "CLIENT"))
                .expect(400, "TERMS_REQUIRED");
        JsonNode login = post("/api/auth/otp/verify", null, Map.of("email", email, "code", code, "role", "CLIENT",
                "acceptTerms", true, "termsVersion", "2026-11-01", "privacyVersion", "2026-11-01")).ok();

        String token = login.path("token").asText();
        JsonNode user = login.path("user");
        assertThat(user.path("role").asText()).isEqualTo("CLIENT");
        assertThat(user.path("companyStatus").asText()).isEqualTo("DRAFT");
        assertThat(user.path("consents").path("TERMS").asBoolean()).isTrue();
        assertThat(user.path("consents").path("PRIVACY").asBoolean()).isTrue();
        assertThat(user.path("consents").path("CROSS_BORDER_PROCESSING").asBoolean()).isFalse();

        post("/api/auth/otp/verify", null, Map.of("email", email, "code", code)).expect(400, "OTP_INVALID");

        post("/api/auth/logout", token, null).ok();
        get("/api/me", token).expect(401, "UNAUTHORIZED");

        String secondCode = requestCode(email);
        JsonNode again = post("/api/auth/otp/verify", null, Map.of("email", email, "code", secondCode)).ok();
        assertThat(again.path("user").path("id").asLong()).isEqualTo(user.path("id").asLong());
        assertThat(get("/api/me", again.path("token").asText()).ok().path("email").asText()).isEqualTo(email);
    }

    @Test
    void engineerRegistersWithChineseLocaleAndDraftProfile() {
        String token = register(uniqueEmail("engineer"), "ENGINEER");
        JsonNode me = get("/api/me", token).ok();
        assertThat(me.path("role").asText()).isEqualTo("ENGINEER");
        assertThat(me.path("locale").asText()).isEqualTo("zh-CN");
        assertThat(me.path("engineerProfileStatus").asText()).isEqualTo("DRAFT");
        assertThat(get("/api/engineer/profile", token).ok().path("alias").asText()).matches("QE-[2-9A-Z]{4}");
        get("/api/client/company", token).expect(403, "FORBIDDEN");
    }

    @Test
    void fiveWrongCodesLockTheEmail() {
        String email = uniqueEmail("locked");
        String code = requestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int attempt = 1; attempt < 5; attempt++) {
            post("/api/auth/otp/verify", null, Map.of("email", email, "code", wrong)).expect(400, "OTP_INVALID");
        }
        post("/api/auth/otp/verify", null, Map.of("email", email, "code", wrong)).expect(429, "OTP_LOCKED");
        post("/api/auth/otp/verify", null, Map.of("email", email, "code", code, "role", "CLIENT",
                "acceptTerms", true)).expect(429, "OTP_LOCKED");
        post("/api/auth/otp/request", null, Map.of("email", email)).expect(429, "OTP_LOCKED");
    }

    @Test
    void validationErrorsUseTheEnvelope() {
        JsonNode body = post("/api/auth/otp/request", null, Map.of("email", "not-an-email"))
                .expect(400, "VALIDATION_ERROR").body();
        assertThat(body.path("data").path("fields").path("email").asText()).isEqualTo("Email");
        assertThat(body.path("message").asText()).isNotBlank();
    }
}
