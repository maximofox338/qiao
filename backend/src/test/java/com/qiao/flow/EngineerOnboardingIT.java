package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EngineerOnboardingIT extends IntegrationTest {

    @Test
    void profileNeedsDeclarationAndCrossBorderConsentThenOpsApproves() {
        String token = register(uniqueEmail("engineer"), "ENGINEER");
        post("/api/engineer/profile/submit", token, null).expect(400, "VALIDATION_ERROR");

        Map<String, Object> profile = profileBody();
        profile.put("declarationNoRestrictions", false);
        put("/api/engineer/profile", token, profile).ok();
        post("/api/engineer/profile/submit", token, null).expect(400, "DECLARATIONS_REQUIRED");

        profile.put("declarationNoRestrictions", true);
        JsonNode saved = put("/api/engineer/profile", token, profile).ok();
        assertThat(saved.path("declarationAt").isNull()).isFalse();
        post("/api/engineer/profile/submit", token, null).expect(409, "CONSENT_REQUIRED");

        grantConsent(token, "CROSS_BORDER_PROCESSING");
        assertThat(get("/api/me/consents", token).ok().toString()).contains("CROSS_BORDER_PROCESSING");
        JsonNode submitted = post("/api/engineer/profile/submit", token, null).ok();
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING_REVIEW");
        put("/api/engineer/profile", token, profile).expect(409, "INVALID_STATE");

        long profileId = submitted.path("id").asLong();
        String admin = adminToken();
        JsonNode adminView = get("/api/admin/engineers/" + profileId, admin).ok();
        assertThat(adminView.path("email").asText()).endsWith("@test.qiao");
        assertThat(adminView.path("cvText").asText()).isNotBlank();

        post("/api/admin/engineers/" + profileId + "/review", admin, Map.of("decision", "APPROVE"))
                .expect(400, "PARTNER_REF_REQUIRED");
        JsonNode approved = post("/api/admin/engineers/" + profileId + "/review", admin,
                Map.of("decision", "APPROVE", "partnerRef", "PARTNER-42")).ok();
        assertThat(approved.path("partnerRef").asText()).isEqualTo("PARTNER-42");
        assertThat(get("/api/me", token).ok().path("engineerProfileStatus").asText()).isEqualTo("APPROVED");
    }

    @Test
    void rejectedProfileReturnsToDraftWhenEdited() {
        String token = register(uniqueEmail("engineer"), "ENGINEER");
        put("/api/engineer/profile", token, profileBody()).ok();
        grantConsent(token, "CROSS_BORDER_PROCESSING");
        long profileId = post("/api/engineer/profile/submit", token, null).ok().path("id").asLong();
        post("/api/admin/engineers/" + profileId + "/review", adminToken(),
                Map.of("decision", "REJECT", "note", "Add more detail")).ok();
        JsonNode profile = get("/api/engineer/profile", token).ok();
        assertThat(profile.path("status").asText()).isEqualTo("REJECTED");
        assertThat(profile.path("reviewNote").asText()).isEqualTo("Add more detail");
        assertThat(put("/api/engineer/profile", token, profileBody()).ok().path("status").asText()).isEqualTo("DRAFT");
    }
}
