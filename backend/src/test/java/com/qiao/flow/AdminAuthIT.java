package com.qiao.flow;

import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthIT extends IntegrationTest {

    @Test
    void bootstrapSuperadminLogsIn() {
        String token = adminToken();
        var me = get("/api/admin/me", token).ok();
        assertThat(me.path("email").asText()).isEqualTo(ADMIN_EMAIL);
        assertThat(me.path("roles").toString()).contains("SUPERADMIN");
        get("/api/admin/dashboard", token).ok();
    }

    @Test
    void adminIsLockedAfterFiveBadPasswords() {
        String email = uniqueEmail("ops");
        String password = "Correct-horse-battery-1";
        post("/api/admin/admins", adminToken(), Map.of("email", email, "password", password, "roles", List.of("OPS"))).ok();

        for (int attempt = 1; attempt <= 5; attempt++) {
            post("/api/admin/auth/login", null, Map.of("email", email, "password", "wrong-password-123"))
                    .expect(401, "INVALID_CREDENTIALS");
        }
        post("/api/admin/auth/login", null, Map.of("email", email, "password", password)).expect(429, "ADMIN_LOCKED");
    }

    @Test
    void parallelBadPasswordsCannotExceedTheLockout() {
        String email = uniqueEmail("ops");
        String password = "Correct-horse-battery-2";
        post("/api/admin/admins", adminToken(), Map.of("email", email, "password", password, "roles", List.of("OPS"))).ok();

        List<Response> responses = parallel(12, () -> post("/api/admin/auth/login", null,
                Map.of("email", email, "password", "wrong-password-123")));
        assertThat(responses).extracting(Response::code).containsOnly("INVALID_CREDENTIALS", "ADMIN_LOCKED");
        assertThat(responses).filteredOn(r -> r.code().equals("INVALID_CREDENTIALS"))
                .as("passwords actually checked").hasSize(5);
        post("/api/admin/auth/login", null, Map.of("email", email, "password", password)).expect(429, "ADMIN_LOCKED");
    }

    @Test
    void adminRolesRestrictSections() {
        String email = uniqueEmail("finance");
        String password = "Finance-password-123";
        post("/api/admin/admins", adminToken(),
                Map.of("email", email, "password", password, "roles", List.of("FINANCE"))).ok();
        String finance = post("/api/admin/auth/login", null, Map.of("email", email, "password", password))
                .ok().path("token").asText();

        get("/api/admin/settlements", finance).ok();
        get("/api/admin/companies", finance).expect(403, "FORBIDDEN");
        post("/api/admin/admins", finance, Map.of("email", uniqueEmail("x"), "password", password,
                "roles", List.of("OPS"))).expect(403, "FORBIDDEN");
        get("/api/me", finance).expect(403, "FORBIDDEN");
    }

    @Test
    void shortAdminPasswordsAreRejected() {
        post("/api/admin/admins", adminToken(), Map.of("email", uniqueEmail("short"), "password", "short",
                "roles", List.of("OPS"))).expect(400, "VALIDATION_ERROR");
    }
}
