package com.qiao.flow;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin self-service password change (other sessions end at once) and the SUPERADMIN disable / enable lifecycle
 * (a disabled admin's tokens stop working on the next request and are not revived by enabling it again).
 */
class AdminLifecycleIT extends IntegrationTest {

    private static final String TEST_JWT_SECRET = "test-only-jwt-secret-0123456789abcdef0123456789";

    @Test
    void changingThePasswordEndsEveryOtherSession() {
        String email = uniqueEmail("ops");
        String password = "First-password-2026";
        long id = createAdmin(email, password, "OPS");
        String laptop = login(email, password);
        String phone = login(email, password);

        post("/api/admin/me/password", laptop, Map.of("currentPassword", "not-my-password-1", "newPassword",
                "Second-password-2026")).expect(400, "CURRENT_PASSWORD_INVALID");
        post("/api/admin/me/password", laptop, Map.of("currentPassword", password, "newPassword", "short"))
                .expect(400, "VALIDATION_ERROR");
        JsonNode same = post("/api/admin/me/password", laptop, Map.of("currentPassword", password,
                "newPassword", password)).expect(400, "VALIDATION_ERROR").data();
        assertThat(same.path("fields").path("newPassword").asText()).isEqualTo("SAME_AS_CURRENT");
        // failed attempts change nothing: both sessions still work
        get("/api/admin/me", laptop).ok();
        get("/api/admin/me", phone).ok();

        JsonNode changed = post("/api/admin/me/password", laptop, Map.of("currentPassword", password,
                "newPassword", "Second-password-2026")).ok();
        String renewed = changed.path("token").asText();
        assertThat(changed.path("admin").path("email").asText()).isEqualTo(email);

        get("/api/admin/me", phone).expect(401, "UNAUTHORIZED");
        get("/api/admin/me", laptop).expect(401, "UNAUTHORIZED");
        assertThat(get("/api/admin/me", renewed).ok().path("id").asLong()).isEqualTo(id);
        post("/api/admin/auth/login", null, Map.of("email", email, "password", password))
                .expect(401, "INVALID_CREDENTIALS");
        login(email, "Second-password-2026");

        String admin = adminToken();
        assertThat(auditFor(admin, "ADMIN_PASSWORD_CHANGED", id)).hasSize(1);
        JsonNode failed = auditFor(admin, "ADMIN_PASSWORD_CHANGE_FAILED", id);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).path("detail").path("reason").asText()).isEqualTo("BAD_CURRENT_PASSWORD");
        assertThat(failed.toString()).doesNotContain("not-my-password-1");
    }

    @Test
    void passwordChangeAttemptsAreRateLimitedPerAdmin() {
        String email = uniqueEmail("ops");
        createAdmin(email, "Rate-limited-password-1", "OPS");
        String token = login(email, "Rate-limited-password-1");
        for (int attempt = 1; attempt <= 5; attempt++) {
            post("/api/admin/me/password", token, Map.of("currentPassword", "wrong-password-" + attempt,
                    "newPassword", "Another-password-2026")).expect(400, "CURRENT_PASSWORD_INVALID");
        }
        post("/api/admin/me/password", token, Map.of("currentPassword", "Rate-limited-password-1",
                "newPassword", "Another-password-2026")).expect(429, "RATE_LIMITED");
        // the login itself is not locked by these attempts
        login(email, "Rate-limited-password-1");
    }

    @Test
    void tokensIssuedBeforeTokenVersionsExistedKeepWorkingUntilThePasswordChanges() {
        String email = uniqueEmail("ops");
        long id = createAdmin(email, "Legacy-token-password-1", "OPS");
        // a token as the previous release issued it: no "tv" claim
        String legacy = JWT.create().withSubject(Long.toString(id)).withClaim("typ", "admin")
                .withClaim("roles", List.of("OPS")).withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(Instant.now()).withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256(TEST_JWT_SECRET));
        get("/api/admin/me", legacy).ok();

        String current = login(email, "Legacy-token-password-1");
        post("/api/admin/me/password", current, Map.of("currentPassword", "Legacy-token-password-1",
                "newPassword", "Legacy-token-password-2")).ok();
        get("/api/admin/me", legacy).expect(401, "UNAUTHORIZED");
    }

    @Test
    void superadminDisablesAndEnablesAnotherAdmin() {
        String superadmin = adminToken();
        long superadminId = get("/api/admin/me", superadmin).ok().path("id").asLong();
        String email = uniqueEmail("ops");
        long id = createAdmin(email, "Disable-me-password-1", "OPS");
        String opsToken = login(email, "Disable-me-password-1");
        get("/api/admin/dashboard", opsToken).ok();

        // an OPS admin cannot manage admins
        post("/api/admin/admins/" + superadminId + "/disable", opsToken, null).expect(403, "FORBIDDEN");

        JsonNode disabled = post("/api/admin/admins/" + id + "/disable", superadmin, null).ok();
        assertThat(disabled.path("status").asText()).isEqualTo("DISABLED");
        // the existing token stops working at once, and a new login is refused
        get("/api/admin/dashboard", opsToken).expect(401, "UNAUTHORIZED");
        post("/api/admin/auth/login", null, Map.of("email", email, "password", "Disable-me-password-1"))
                .expect(403, "ACCOUNT_DISABLED");
        // idempotent: disabling again changes nothing and writes no second audit entry
        post("/api/admin/admins/" + id + "/disable", superadmin, null).ok();
        assertThat(rowOf(get("/api/admin/admins", superadmin).ok(), id).path("status").asText()).isEqualTo("DISABLED");

        JsonNode enabled = post("/api/admin/admins/" + id + "/enable", superadmin, null).ok();
        assertThat(enabled.path("status").asText()).isEqualTo("ACTIVE");
        // the old session is not revived; a new login works
        get("/api/admin/dashboard", opsToken).expect(401, "UNAUTHORIZED");
        get("/api/admin/dashboard", login(email, "Disable-me-password-1")).ok();

        JsonNode disabledAudit = auditFor(superadmin, "ADMIN_DISABLED", id);
        assertThat(disabledAudit).hasSize(1);
        assertThat(disabledAudit.get(0).path("actorId").asLong()).isEqualTo(superadminId);
        assertThat(disabledAudit.get(0).path("detail").path("email").asText()).isEqualTo(email);
        assertThat(auditFor(superadmin, "ADMIN_ENABLED", id)).hasSize(1);
    }

    @Test
    void aSuperadminCannotDisableThemselvesButCanDisableAnotherSuperadmin() {
        String email = uniqueEmail("super");
        long id = createAdmin(email, "Second-superadmin-pass-1", "SUPERADMIN");
        String second = login(email, "Second-superadmin-pass-1");

        post("/api/admin/admins/" + id + "/disable", second, null).expect(409, "CANNOT_DISABLE_SELF");
        get("/api/admin/me", second).ok();
        post("/api/admin/admins/999999999/disable", adminToken(), null).expect(404, "NOT_FOUND");
        post("/api/admin/admins/999999999/enable", adminToken(), null).expect(404, "NOT_FOUND");

        // the bootstrap SUPERADMIN stays active, so the second one can be disabled
        post("/api/admin/admins/" + id + "/disable", adminToken(), null).ok();
        get("/api/admin/me", second).expect(401, "UNAUTHORIZED");
    }

    @Test
    void twoSuperadminsDisablingEachOtherAtOnceAreSerialized() {
        String emailA = uniqueEmail("super");
        String emailB = uniqueEmail("super");
        long a = createAdmin(emailA, "Parallel-superadmin-A-1", "SUPERADMIN");
        long b = createAdmin(emailB, "Parallel-superadmin-B-1", "SUPERADMIN");
        String tokenA = login(emailA, "Parallel-superadmin-A-1");
        String tokenB = login(emailB, "Parallel-superadmin-B-1");

        AtomicInteger next = new AtomicInteger();
        List<Response> responses = parallel(2, () -> next.getAndIncrement() == 0
                ? post("/api/admin/admins/" + b + "/disable", tokenA, null)
                : post("/api/admin/admins/" + a + "/disable", tokenB, null));
        // whoever comes second finds its own account already disabled (under the row locks, or already when its token
        // is checked): exactly one of the two disables happens
        List<String> codes = responses.stream().map(Response::code).toList();
        assertThat(codes).containsOnlyOnce("OK");
        assertThat(codes).filteredOn(c -> !c.equals("OK")).singleElement()
                .isIn("ACCOUNT_DISABLED", "UNAUTHORIZED");
        JsonNode admins = get("/api/admin/admins", adminToken()).ok();
        assertThat(List.of(rowOf(admins, a).path("status").asText(), rowOf(admins, b).path("status").asText()))
                .containsExactlyInAnyOrder("ACTIVE", "DISABLED");
    }

    // ------------------------------------------------------------------ helpers

    private long createAdmin(String email, String password, String role) {
        return post("/api/admin/admins", adminToken(), Map.of("email", email, "password", password,
                "roles", List.of(role))).ok().path("id").asLong();
    }

    private String login(String email, String password) {
        return post("/api/admin/auth/login", null, Map.of("email", email, "password", password))
                .ok().path("token").asText();
    }

    private JsonNode auditFor(String admin, String action, long adminId) {
        JsonNode records = get("/api/admin/audit?action=" + action + "&size=100", admin).ok().path("records");
        var matching = objectMapper.createArrayNode();
        records.forEach(r -> {
            if (r.path("entityId").asText().equals(Long.toString(adminId))) {
                matching.add(r);
            }
        });
        return matching;
    }

    private static JsonNode rowOf(JsonNode admins, long id) {
        for (JsonNode admin : admins) {
            if (admin.path("id").asLong() == id) {
                return admin;
            }
        }
        throw new AssertionError("admin " + id + " not listed");
    }
}
