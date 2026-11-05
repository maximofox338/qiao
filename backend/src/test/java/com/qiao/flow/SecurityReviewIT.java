package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Regression tests for the security review: funding rules, cross-party access to a contract and input validation.
 */
class SecurityReviewIT extends IntegrationTest {

    private record ActiveContract(String client, String engineer, long contractId, List<Long> milestoneIds) {
    }

    @Test
    void blockedCompanyCannotFundItsMilestones() {
        ActiveContract contract = activeContract();
        long companyId = get("/api/client/company", contract.client()).ok().path("id").asLong();
        post("/api/admin/companies/" + companyId + "/review", adminToken(),
                Map.of("decision", "REJECT", "sanctionsStatus", "HIT", "note", "sanctions list match")).ok();

        post("/api/client/milestones/" + contract.milestoneIds().getFirst() + "/checkout", contract.client(), null)
                .expect(409, "COMPANY_NOT_APPROVED");
    }

    @Test
    void secondCheckoutOfAFundedMilestoneCannotBeConfirmed() {
        ActiveContract contract = activeContract();
        long milestoneId = contract.milestoneIds().getFirst();
        long first = post("/api/client/milestones/" + milestoneId + "/checkout", contract.client(), null).ok()
                .path("paymentId").asLong();
        long second = post("/api/client/milestones/" + milestoneId + "/checkout", contract.client(), null).ok()
                .path("paymentId").asLong();

        post("/api/payments/fake/" + first + "/confirm", contract.client(), null).ok();
        post("/api/payments/fake/" + second + "/confirm", contract.client(), null).expect(409, "PAYMENT_ALREADY_PAID");
        assertThat(get("/api/payments/" + second, contract.client()).ok().path("status").asText()).isEqualTo("PENDING");
        assertThat(get("/api/payments/" + first, contract.client()).ok().path("status").asText()).isEqualTo("PAID");
    }

    @Test
    void otherClientsAndEngineersCannotReachTheContract() throws Exception {
        ActiveContract contract = activeContract();
        long contractId = contract.contractId();
        long milestoneId = contract.milestoneIds().getFirst();
        long paymentId = post("/api/client/milestones/" + milestoneId + "/checkout", contract.client(), null).ok()
                .path("paymentId").asLong();
        long attachmentId = upload(contract.engineer(), contractId);
        String otherClient = approvedClient();
        String otherEngineer = approvedEngineer();

        get("/api/client/contracts/" + contractId, otherClient).expect(404, "NOT_FOUND");
        get("/api/contracts/" + contractId + "/messages", otherClient).expect(404, "NOT_FOUND");
        post("/api/contracts/" + contractId + "/messages", otherClient, Map.of("body", "hi")).expect(404, "NOT_FOUND");
        get("/api/attachments/" + attachmentId, otherClient).expect(404, "NOT_FOUND");
        post("/api/client/milestones/" + milestoneId + "/checkout", otherClient, null).expect(404, "NOT_FOUND");
        get("/api/payments/" + paymentId, otherClient).expect(404, "NOT_FOUND");
        post("/api/payments/fake/" + paymentId + "/confirm", otherClient, null).expect(404, "NOT_FOUND");

        get("/api/engineer/contracts/" + contractId, otherEngineer).expect(404, "NOT_FOUND");
        get("/api/contracts/" + contractId + "/messages", otherEngineer).expect(404, "NOT_FOUND");
        get("/api/attachments/" + attachmentId, otherEngineer).expect(404, "NOT_FOUND");
        post("/api/engineer/milestones/" + milestoneId + "/submit", otherEngineer, Map.of("note", "x"))
                .expect(404, "NOT_FOUND");

        // the contract's own engineer cannot see or confirm the client's payment either
        get("/api/payments/" + paymentId, contract.engineer()).expect(404, "NOT_FOUND");
        post("/api/payments/fake/" + paymentId + "/confirm", contract.engineer(), null).expect(404, "NOT_FOUND");
        get("/api/client/contracts/" + contractId, contract.engineer()).expect(403, "FORBIDDEN");
        get("/api/partner/settlements", contract.engineer()).expect(403, "FORBIDDEN");
        get("/api/admin/dashboard", contract.client()).expect(403, "FORBIDDEN");
    }

    @Test
    void nullListElementsAreValidationErrorsNotServerErrors() {
        String client = approvedClient();
        LocalDate start = LocalDate.now().plusDays(3);
        Map<String, Object> project = projectBody("Null milestone", "web-dev", new BigDecimal("500"), start,
                start.plusMonths(1), List.of(milestone("Page", start.plusWeeks(2), "500")), false);
        project.put("milestones", Collections.singletonList(null));
        post("/api/client/projects", client, project).expect(400, "VALIDATION_ERROR");

        String engineer = approvedEngineer();
        long projectId = openProject(client);
        post("/api/engineer/projects/" + projectId + "/applications", engineer,
                Map.of("coverNote", "x", "prices", Collections.singletonList(null))).expect(400, "VALIDATION_ERROR");
    }

    private ActiveContract activeContract() {
        String client = approvedClient();
        String engineer = approvedEngineer();
        long projectId = openProject(client);
        List<Map<String, Object>> prices = new ArrayList<>();
        for (JsonNode m : get("/api/engineer/projects/" + projectId, engineer).ok().path("milestones")) {
            prices.add(Map.of("milestoneId", m.path("id").asLong(), "talentAmountUsd", m.path("talentAmountUsd").decimalValue()));
        }
        long applicationId = post("/api/engineer/projects/" + projectId + "/applications", engineer,
                Map.of("coverNote", "Ready to start.", "prices", prices)).ok().path("id").asLong();
        post("/api/client/applications/" + applicationId + "/select", client, null).ok();
        JsonNode hired = post("/api/client/applications/" + applicationId + "/hire", client, null).ok();
        long contractId = hired.path("id").asLong();
        post("/api/client/contracts/" + contractId + "/documents/" + hired.path("documents").get(0).path("id").asLong()
                + "/accept", client, null).ok();
        long engineerDocId = get("/api/engineer/contracts/" + contractId, engineer).ok()
                .path("documents").get(0).path("id").asLong();
        JsonNode active = post("/api/engineer/contracts/" + contractId + "/documents/" + engineerDocId + "/accept",
                engineer, null).ok();
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");
        List<Long> milestoneIds = new ArrayList<>();
        active.path("milestones").forEach(m -> milestoneIds.add(m.path("id").asLong()));
        return new ActiveContract(client, engineer, contractId, milestoneIds);
    }

    private long upload(String token, long contractId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "progress notes".getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/contracts/" + contractId + "/attachments").file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }
}
