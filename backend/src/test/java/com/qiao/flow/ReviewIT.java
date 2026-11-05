package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviews (one per side once a contract is COMPLETED): ratings on the pseudonymized profile, texts for admins only.
 */
class ReviewIT extends IntegrationTest {

    @Test
    void bothSidesReviewACompletedContract() {
        String client = approvedClient();
        String engineer = approvedEngineer();
        String admin = adminToken();
        long projectId = openProject(client);
        long contractId = activeContract(client, projectId, engineer);

        post("/api/client/contracts/" + contractId + "/review", client, Map.of("stars", 5)).expect(409, "CONTRACT_NOT_COMPLETED");
        post("/api/engineer/contracts/" + contractId + "/review", engineer, Map.of("stars", 5)).expect(409, "CONTRACT_NOT_COMPLETED");
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("canReview").asBoolean()).isFalse();
        complete(client, engineer, contractId);

        JsonNode clientView = get("/api/client/contracts/" + contractId, client).ok();
        assertThat(clientView.path("canReview").asBoolean()).isTrue();
        assertThat(clientView.path("myReview").isNull()).isTrue();
        post("/api/client/contracts/" + contractId + "/review", client, Map.of("stars", 0)).expect(400, "VALIDATION_ERROR");
        post("/api/client/contracts/" + contractId + "/review", client, Map.of("stars", 6)).expect(400, "VALIDATION_ERROR");
        post("/api/engineer/contracts/" + contractId + "/review", client, Map.of("stars", 5)).expect(403, "FORBIDDEN");
        JsonNode review = post("/api/client/contracts/" + contractId + "/review", client,
                Map.of("stars", 4, "comment", "Clean code, delivered on time.")).ok();
        assertThat(review.path("stars").asInt()).isEqualTo(4);
        post("/api/client/contracts/" + contractId + "/review", client, Map.of("stars", 1)).expect(409, "ALREADY_REVIEWED");
        clientView = get("/api/client/contracts/" + contractId, client).ok();
        assertThat(clientView.path("canReview").asBoolean()).isFalse();
        assertThat(clientView.path("myReview").path("comment").asText()).isEqualTo("Clean code, delivered on time.");

        // the rating shows on the engineer's pseudonymized profile
        JsonNode profile = get("/api/engineer/profile", engineer).ok();
        assertThat(profile.path("ratingCount").asInt()).isEqualTo(1);
        assertThat(profile.path("ratingAvg").decimalValue()).isEqualByComparingTo("4.00");

        // the engineer reviews the client; texts about the other side are for the back-office only
        JsonNode engineerView = get("/api/engineer/contracts/" + contractId, engineer).ok();
        assertThat(engineerView.path("canReview").asBoolean()).isTrue();
        assertThat(engineerView.toString()).doesNotContain("Clean code");
        post("/api/engineer/contracts/" + contractId + "/review", engineer,
                Map.of("stars", 5, "comment", "Clear requirements and fast feedback.")).ok();
        post("/api/engineer/contracts/" + contractId + "/review", engineer, Map.of("stars", 5)).expect(409, "ALREADY_REVIEWED");
        assertThat(get("/api/client/contracts/" + contractId, client).ok().toString()).doesNotContain("fast feedback");
        assertThat(get("/api/engineer/contracts/" + contractId, engineer).ok().path("myReview").path("stars").asInt()).isEqualTo(5);

        JsonNode adminList = get("/api/admin/reviews?side=ENGINEER&size=100", admin).ok();
        assertThat(adminList.toString()).contains("fast feedback").contains("Acme Robotics");
        assertThat(get("/api/admin/reviews?side=CLIENT&size=100", admin).ok().toString()).contains("Clean code");
        assertThat(get("/api/admin/audit?action=REVIEW_CREATED", admin).ok().path("total").asLong()).isPositive();
        String finance = financeAdmin();
        get("/api/admin/reviews", finance).expect(403, "FORBIDDEN");

        // a second contract averages the rating
        long second = activeContract(client, openProject(client), engineer);
        complete(client, engineer, second);
        post("/api/client/contracts/" + second + "/review", client, Map.of("stars", 5)).ok();
        profile = get("/api/engineer/profile", engineer).ok();
        assertThat(profile.path("ratingCount").asInt()).isEqualTo(2);
        assertThat(profile.path("ratingAvg").decimalValue()).isEqualByComparingTo("4.50");
    }

    @Test
    void aTeamReviewRatesEveryMemberAndOnlyTheLeadReviewsTheClient() {
        String client = approvedClient();
        long projectId = openProject(client);
        String lead = approvedEngineer();
        String member = approvedEngineer();
        String memberAlias = get("/api/engineer/profile", member).ok().path("alias").asText();
        String leadAlias = get("/api/engineer/profile", lead).ok().path("alias").asText();
        long teamId = post("/api/engineer/teams", lead, Map.of("name", "Reviewed team")).ok().path("id").asLong();
        post("/api/engineer/teams/" + teamId + "/invitations", lead, Map.of("alias", memberAlias)).ok();
        post("/api/engineer/teams/" + teamId + "/invitations/accept", member, null).ok();
        long applicationId = post("/api/engineer/projects/" + projectId + "/team-applications", lead,
                Map.of("teamId", teamId, "coverNote", "team", "prices", prices(lead, projectId),
                        "shares", List.of(Map.of("alias", leadAlias, "sharePct", 50), Map.of("alias", memberAlias, "sharePct", 50))))
                .ok().path("id").asLong();
        post("/api/client/applications/" + applicationId + "/select", client, null).ok();
        long contractId = post("/api/client/applications/" + applicationId + "/hire", client, null).ok().path("id").asLong();
        acceptOwnDocuments(client, contractId, lead, member);
        complete(client, lead, contractId);

        post("/api/client/contracts/" + contractId + "/review", client, Map.of("stars", 3, "comment", "OK")).ok();
        for (String engineer : List.of(lead, member)) {
            JsonNode profile = get("/api/engineer/profile", engineer).ok();
            assertThat(profile.path("ratingCount").asInt()).isEqualTo(1);
            assertThat(profile.path("ratingAvg").decimalValue()).isEqualByComparingTo("3.00");
        }
        assertThat(get("/api/engineer/contracts/" + contractId, member).ok().path("canReview").asBoolean()).isFalse();
        post("/api/engineer/contracts/" + contractId + "/review", member, Map.of("stars", 5)).expect(403, "TEAM_LEAD_ONLY");
        post("/api/engineer/contracts/" + contractId + "/review", lead, Map.of("stars", 5)).ok();
        assertThat(get("/api/engineer/contracts/" + contractId, member).ok().path("myReview").path("stars").asInt()).isEqualTo(5);
    }

    // ------------------------------------------------------------------ helpers

    private List<Map<String, Object>> prices(String engineer, long projectId) {
        List<Map<String, Object>> prices = new ArrayList<>();
        get("/api/engineer/projects/" + projectId, engineer).ok().path("milestones").forEach(m -> prices.add(
                Map.of("milestoneId", m.path("id").asLong(), "talentAmountUsd", m.path("talentAmountUsd").decimalValue())));
        return prices;
    }

    private long activeContract(String client, long projectId, String engineer) {
        long applicationId = post("/api/engineer/projects/" + projectId + "/applications", engineer,
                Map.of("coverNote", "Ready", "prices", prices(engineer, projectId))).ok().path("id").asLong();
        post("/api/client/applications/" + applicationId + "/select", client, null).ok();
        long contractId = post("/api/client/applications/" + applicationId + "/hire", client, null).ok().path("id").asLong();
        acceptOwnDocuments(client, contractId, engineer);
        return contractId;
    }

    private void acceptOwnDocuments(String client, long contractId, String... engineers) {
        long clientDoc = get("/api/client/contracts/" + contractId, client).ok().path("documents").get(0).path("id").asLong();
        post("/api/client/contracts/" + contractId + "/documents/" + clientDoc + "/accept", client, null).ok();
        for (String engineer : engineers) {
            long doc = get("/api/engineer/contracts/" + contractId, engineer).ok().path("documents").get(0).path("id").asLong();
            post("/api/engineer/contracts/" + contractId + "/documents/" + doc + "/accept", engineer, null).ok();
        }
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("status").asText()).isEqualTo("ACTIVE");
    }

    /**
     * Funds, delivers (as the engineer or lead) and accepts every milestone.
     */
    private void complete(String client, String engineer, long contractId) {
        for (JsonNode m : get("/api/client/contracts/" + contractId, client).ok().path("milestones")) {
            long milestoneId = m.path("id").asLong();
            long paymentId = post("/api/client/milestones/" + milestoneId + "/checkout", client, null).ok().path("paymentId").asLong();
            post("/api/payments/fake/" + paymentId + "/confirm", client, null).ok();
            post("/api/engineer/milestones/" + milestoneId + "/submit", engineer, Map.of("note", "done")).ok();
            post("/api/client/milestones/" + milestoneId + "/accept", client, null).ok();
        }
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("status").asText()).isEqualTo("COMPLETED");
    }

    private String financeAdmin() {
        String email = uniqueEmail("finance");
        String password = "Finance-password-456";
        post("/api/admin/admins", adminToken(), Map.of("email", email, "password", password, "roles", List.of("FINANCE"))).ok();
        return post("/api/admin/auth/login", null, Map.of("email", email, "password", password)).ok().path("token").asText();
    }
}
