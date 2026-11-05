package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRulesIT extends IntegrationTest {

    private final LocalDate start = LocalDate.now().plusDays(3);

    @Test
    void companyDeclarationsGoToReviewAndAdminApproves() {
        String token = register(uniqueEmail("client"), "CLIENT");
        post("/api/client/company/declarations", token, Map.of("version", "2026-11-01", "accepted", true))
                .expect(400, "VALIDATION_ERROR");
        put("/api/client/company", token, Map.of("name", "Globex", "country", "es")).ok();
        post("/api/client/company/declarations", token, Map.of("version", "1999-01-01", "accepted", true))
                .expect(400, "VALIDATION_ERROR");
        JsonNode company = post("/api/client/company/declarations", token,
                Map.of("version", "2026-11-01", "accepted", true)).ok();
        assertThat(company.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(company.path("country").asText()).isEqualTo("ES");
        put("/api/client/company", token, Map.of("name", "Globex 2", "country", "ES")).expect(409, "INVALID_STATE");

        long companyId = company.path("id").asLong();
        String admin = adminToken();
        assertThat(get("/api/admin/companies?status=PENDING_REVIEW&size=100", admin).ok().path("records").toString())
                .contains("Globex");
        post("/api/admin/companies/" + companyId + "/review", admin,
                Map.of("decision", "APPROVE", "sanctionsStatus", "HIT")).expect(400, "SANCTIONS_NOT_CLEAR");
        JsonNode reviewed = post("/api/admin/companies/" + companyId + "/review", admin,
                Map.of("decision", "APPROVE", "sanctionsStatus", "CLEAR", "note", "screened")).ok();
        assertThat(reviewed.path("status").asText()).isEqualTo("APPROVED");
        assertThat(reviewed.path("declarationsHash").asText()).hasSize(64);
        assertThat(get("/api/me", token).ok().path("companyStatus").asText()).isEqualTo("APPROVED");
    }

    @Test
    void submittingRequiresAnApprovedCompanyButDraftsAreAllowed() {
        String token = register(uniqueEmail("client"), "CLIENT");
        Map<String, Object> body = projectBody("Landing page", "web-dev", new BigDecimal("500"), start,
                start.plusMonths(1), List.of(milestone("Page", start.plusWeeks(2), "500")), true);
        post("/api/client/projects", token, body).expect(409, "COMPANY_NOT_APPROVED");
        body.put("submit", false);
        assertThat(post("/api/client/projects", token, body).ok().path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void structuralRulesRejectJobLikeProjects() {
        String token = approvedClient();
        List<Map<String, Object>> one = List.of(milestone("Delivery", start.plusMonths(1), "1000"));

        post("/api/client/projects", token, projectBody("Token platform", "crypto", new BigDecimal("1000"), start,
                start.plusMonths(2), one, true)).expect(400, "PROHIBITED_CATEGORY");
        post("/api/client/projects", token, projectBody("App", "web-dev", new BigDecimal("1200"), start,
                start.plusMonths(2), one, true)).expect(400, "MILESTONE_SUM_MISMATCH");
        post("/api/client/projects", token, projectBody("App", "web-dev", new BigDecimal("1000"), start,
                start.plusMonths(13), one, true)).expect(400, "DURATION_TOO_LONG");
        post("/api/client/projects", token, projectBody("App", "web-dev", new BigDecimal("1000"), start,
                null, one, true)).expect(400, "END_DATE_REQUIRED");
        post("/api/client/projects", token, projectBody("App", "web-dev", new BigDecimal("1000"), start,
                start.plusMonths(2), List.of(), true)).expect(400, "MILESTONES_REQUIRED");

        Map<String, Object> undeclared = projectBody("App", "web-dev", new BigDecimal("1000"), start,
                start.plusMonths(2), one, true);
        undeclared.put("declarations", Map.of("noControlledTech", true, "noBulkUsSensitiveData", true,
                "noEmployment", false, "noProhibitedCategory", true));
        post("/api/client/projects", token, undeclared).expect(400, "DECLARATIONS_REQUIRED");

        JsonNode outOfRange = post("/api/client/projects", token, projectBody("App", "web-dev",
                new BigDecimal("1000"), start, start.plusMonths(1),
                List.of(milestone("Late", start.plusMonths(2), "1000")), true)).expect(400, "VALIDATION_ERROR").data();
        assertThat(outOfRange.path("fields").path("milestones[0].dueDate").asText()).isEqualTo("DUE_OUTSIDE_RANGE");
    }

    @Test
    void employmentWordingIsFlaggedNotRejectedAndAdminApproves() {
        String token = approvedClient();
        Map<String, Object> body = projectBody("Mobile app", "mobile-dev", new BigDecimal("3000"), start,
                start.plusMonths(3), List.of(milestone("MVP", start.plusMonths(3), "3000")), true);
        body.put("description", "Looking for a full-time developer, 40 hours a week, with a drone module.");
        JsonNode project = post("/api/client/projects", token, body).ok();
        assertThat(project.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(project.path("flags").toString())
                .contains("{\"type\":\"EMPLOYMENT\",\"keyword\":\"full-time\"}")
                .contains("\"keyword\":\"40 hours a week\"")
                .contains("{\"type\":\"SENSITIVE\",\"keyword\":\"drone\"}");

        long projectId = project.path("id").asLong();
        String admin = adminToken();
        JsonNode changes = post("/api/admin/projects/" + projectId + "/review", admin,
                Map.of("decision", "REQUEST_CHANGES", "note", "Remove employment wording")).ok();
        assertThat(changes.path("status").asText()).isEqualTo("CHANGES_REQUESTED");

        body.put("description", "Build an MVP of the mobile app with offline sync.");
        assertThat(put("/api/client/projects/" + projectId, token, body).ok().path("flags").size()).isZero();
        JsonNode approved = post("/api/admin/projects/" + projectId + "/review", admin,
                Map.of("decision", "APPROVE")).ok();
        assertThat(approved.path("status").asText()).isEqualTo("OPEN");

        String engineer = register(uniqueEmail("engineer"), "ENGINEER");
        JsonNode visible = get("/api/engineer/projects/" + projectId, engineer).ok();
        assertThat(visible.path("talentBudgetUsd").decimalValue()).isEqualByComparingTo("2400.00");
        assertThat(visible.path("clientCountry").asText()).isEqualTo("US");
        assertThat(visible.has("budgetUsd")).isFalse();
    }

    @Test
    void adminCannotApproveProjectWhoseCategoryWasDisallowed() {
        String token = approvedClient();
        long projectId = post("/api/client/projects", token, projectBody("Docs", "tech-writing",
                new BigDecimal("800"), start, start.plusMonths(1), List.of(milestone("Manual", start.plusWeeks(3), "800")),
                true)).ok().path("id").asLong();
        String admin = adminToken();
        Map<String, Object> category = Map.of("allowed", false, "nameEn", "Technical writing & localization",
                "nameEs", "Redacción técnica y localización", "nameZh", "技术写作与本地化");
        try {
            put("/api/admin/categories/tech-writing", admin, category).ok();
            post("/api/admin/projects/" + projectId + "/review", admin, Map.of("decision", "APPROVE"))
                    .expect(409, "CATEGORY_NOT_ALLOWED");
        } finally {
            put("/api/admin/categories/tech-writing", admin, Map.of("allowed", true,
                    "nameEn", category.get("nameEn"), "nameEs", category.get("nameEs"), "nameZh", category.get("nameZh"))).ok();
        }
    }
}
