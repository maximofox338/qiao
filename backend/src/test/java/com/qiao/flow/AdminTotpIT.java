package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.admin.service.TotpVerifier;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin TOTP enrolment: setup → confirm with a valid code → login with TOTP (no replay of a used time-step) →
 * disable with a valid code; SUPERADMIN reset.
 */
class AdminTotpIT extends IntegrationTest {

    @Test
    void adminEnablesUsesAndDisablesTotpWithoutReplay() throws Exception {
        String email = uniqueEmail("ops");
        String password = "Totp-password-1234";
        post("/api/admin/admins", adminToken(), Map.of("email", email, "password", password, "roles", List.of("OPS"))).ok();
        String token = login(email, password, null).ok().path("token").asText();
        assertThat(get("/api/admin/me", token).ok().path("totpEnabled").asBoolean()).isFalse();

        post("/api/admin/me/totp/enable", token, Map.of("code", "123456")).expect(409, "TOTP_SETUP_REQUIRED");
        JsonNode setup = post("/api/admin/me/totp/setup", token, null).ok();
        String secret = setup.path("secret").asText();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(setup.path("otpauthUri").asText()).startsWith("otpauth://totp/Qiao:")
                .contains("secret=" + secret).contains("issuer=Qiao");
        // a second setup replaces the unconfirmed secret
        String replaced = post("/api/admin/me/totp/setup", token, null).ok().path("secret").asText();
        assertThat(replaced).isNotEqualTo(secret);
        secret = replaced;

        long step = alignedStep();
        post("/api/admin/me/totp/enable", token, Map.of("code", "12345")).expect(400, "VALIDATION_ERROR");
        post("/api/admin/me/totp/enable", token, Map.of("code", TotpVerifier.codeAt(secret, step + 5)))
                .expect(400, "TOTP_CODE_INVALID");
        JsonNode enabled = post("/api/admin/me/totp/enable", token, Map.of("code", TotpVerifier.codeAt(secret, step - 1))).ok();
        assertThat(enabled.path("totpEnabled").asBoolean()).isTrue();
        post("/api/admin/me/totp/setup", token, null).expect(409, "TOTP_ALREADY_ENABLED");

        // login: TOTP required, each time-step accepted once
        login(email, password, null).expect(401, "TOTP_REQUIRED");
        login(email, password, TotpVerifier.codeAt(secret, step - 1)).expect(401, "TOTP_INVALID");
        JsonNode session = login(email, password, TotpVerifier.codeAt(secret, step)).ok();
        assertThat(session.path("admin").path("totpEnabled").asBoolean()).isTrue();
        login(email, password, TotpVerifier.codeAt(secret, step)).expect(401, "TOTP_INVALID");
        token = session.path("token").asText();

        // disabling needs a fresh valid code too
        post("/api/admin/me/totp/disable", token, Map.of("code", TotpVerifier.codeAt(secret, step))).expect(400, "TOTP_CODE_INVALID");
        JsonNode disabled = post("/api/admin/me/totp/disable", token, Map.of("code", TotpVerifier.codeAt(secret, step + 1))).ok();
        assertThat(disabled.path("totpEnabled").asBoolean()).isFalse();
        post("/api/admin/me/totp/disable", token, Map.of("code", TotpVerifier.codeAt(secret, step + 1))).expect(409, "TOTP_NOT_ENABLED");
        login(email, password, null).ok();

        String audit = get("/api/admin/audit?size=100", adminToken()).ok().toString();
        assertThat(audit).contains("ADMIN_TOTP_ENABLED").contains("ADMIN_TOTP_DISABLED").contains("REPLAYED_TOTP");
    }

    @Test
    void superadminResetsAnotherAdminsTotp() throws Exception {
        String superadmin = adminToken();
        String email = uniqueEmail("finance");
        String password = "Totp-password-5678";
        long adminId = post("/api/admin/admins", superadmin, Map.of("email", email, "password", password,
                "roles", List.of("FINANCE"))).ok().path("id").asLong();
        String token = login(email, password, null).ok().path("token").asText();
        String secret = post("/api/admin/me/totp/setup", token, null).ok().path("secret").asText();
        long step = alignedStep();
        post("/api/admin/me/totp/enable", token, Map.of("code", TotpVerifier.codeAt(secret, step))).ok();
        login(email, password, null).expect(401, "TOTP_REQUIRED");

        String other = uniqueEmail("ops");
        post("/api/admin/admins", superadmin, Map.of("email", other, "password", password, "roles", List.of("OPS"))).ok();
        String ops = login(other, password, null).ok().path("token").asText();
        post("/api/admin/admins/" + adminId + "/totp/reset", ops, null).expect(403, "FORBIDDEN");

        JsonNode reset = post("/api/admin/admins/" + adminId + "/totp/reset", superadmin, null).ok();
        assertThat(reset.path("totpEnabled").asBoolean()).isFalse();
        login(email, password, null).ok();
        for (JsonNode a : get("/api/admin/admins", superadmin).ok()) {
            if (a.path("id").asLong() == adminId) {
                assertThat(a.path("totpEnabled").asBoolean()).isFalse();
            }
        }
        assertThat(get("/api/admin/audit?action=ADMIN_TOTP_RESET", superadmin).ok().path("total").asLong()).isPositive();
    }

    private Response login(String email, String password, String totp) {
        Map<String, Object> body = new HashMap<>(Map.of("email", email, "password", password));
        if (totp != null) {
            body.put("totp", totp);
        }
        return post("/api/admin/auth/login", null, body);
    }

    /**
     * The current 30-second step, waiting for the next one when less than 10 s are left, so that step − 1 … step + 1
     * stay inside the server's drift window for the rest of the test.
     */
    private static long alignedStep() throws InterruptedException {
        long into = System.currentTimeMillis() % 30_000;
        if (into > 20_000) {
            Thread.sleep(30_000 - into + 250);
        }
        return System.currentTimeMillis() / 1000 / TotpVerifier.STEP_SECONDS;
    }
}
