package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Partner key rotation without downtime: during a rotation ({@code PARTNER_API_KEY_PREVIOUS} set, see
 * application-test.yml) both keys work, and calls with the old one leave a warning in the audit log so the operator
 * knows the partner has not switched yet.
 */
class PartnerKeyRotationIT extends IntegrationTest {

    private static final String PREVIOUS_KEY = "test-partner-key-previous";

    @Test
    void bothKeysWorkDuringARotationAndTheOldOneIsFlaggedInTheAudit() {
        String admin = adminToken();
        partnerList(PARTNER_KEY).ok();
        JsonNode withCurrent = latestPartnerListing(admin);
        assertThat(withCurrent.path("detail").has("partnerKey")).isFalse();
        assertThat(withCurrent.path("detail").has("warning")).isFalse();

        partnerList(PREVIOUS_KEY).ok();
        JsonNode withPrevious = latestPartnerListing(admin);
        assertThat(withPrevious.path("id").asLong()).isGreaterThan(withCurrent.path("id").asLong());
        assertThat(withPrevious.path("detail").path("partnerKey").asText()).isEqualTo("PREVIOUS");
        assertThat(withPrevious.path("detail").path("warning").asText()).contains("PARTNER_API_KEY_PREVIOUS");
        // the key itself never reaches the audit log
        assertThat(withPrevious.toString()).doesNotContain(PREVIOUS_KEY).doesNotContain(PARTNER_KEY);

        partnerList("test-partner-key-wrong").expect(401, "UNAUTHORIZED");
        partnerList(PARTNER_KEY + "x").expect(401, "UNAUTHORIZED");
        get("/api/partner/settlements", null).expect(401, "UNAUTHORIZED");
    }

    private Response partnerList(String key) {
        return send(MockMvcRequestBuilders.get("/api/partner/settlements").header("X-Partner-Key", key), null, null);
    }

    private JsonNode latestPartnerListing(String admin) {
        return get("/api/admin/audit?action=PARTNER_SETTLEMENTS_LISTED&actorType=PARTNER&size=1", admin).ok()
                .path("records").get(0);
    }
}
