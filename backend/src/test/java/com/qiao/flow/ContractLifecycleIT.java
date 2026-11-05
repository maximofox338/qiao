package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Apply → pseudonymized review → hire → documents → funding → delivery → completion → settlement → deletion.
 */
class ContractLifecycleIT extends IntegrationTest {

    @Test
    void projectGoesFromApplicationToSettlement() throws Exception {
        String client = approvedClient();
        String engineer = approvedEngineer();
        String admin = adminToken();
        long projectId = openProject(client);

        // ---- apply
        JsonNode project = get("/api/engineer/projects/" + projectId, engineer).ok();
        assertThat(project.path("applied").asBoolean()).isFalse();
        assertThat(get("/api/engineer/projects?size=100", engineer).ok().path("records").toString())
                .contains("\"id\":" + projectId);
        List<Map<String, Object>> prices = new ArrayList<>();
        for (JsonNode m : project.path("milestones")) {
            prices.add(Map.of("milestoneId", m.path("id").asLong(),
                    "talentAmountUsd", m.path("talentAmountUsd").decimalValue()));
        }
        Map<String, Object> application = Map.of("coverNote", "I have built three similar dashboards.", "prices", prices);

        post("/api/me/consents", engineer, Map.of("type", "SHARE_PROFILE_PSEUDONYMIZED", "granted", false)).ok();
        post("/api/engineer/projects/" + projectId + "/applications", engineer, application).expect(409, "CONSENT_REQUIRED");
        grantConsent(engineer, "SHARE_PROFILE_PSEUDONYMIZED");
        String unapproved = register(uniqueEmail("engineer"), "ENGINEER");
        post("/api/engineer/projects/" + projectId + "/applications", unapproved, application)
                .expect(409, "PROFILE_NOT_APPROVED");
        JsonNode applied = post("/api/engineer/projects/" + projectId + "/applications", engineer, application).ok();
        assertThat(applied.path("talentTotalUsd").decimalValue()).isEqualByComparingTo("2000.00");
        post("/api/engineer/projects/" + projectId + "/applications", engineer, application).expect(409, "ALREADY_APPLIED");
        long applicationId = applied.path("id").asLong();

        // ---- client sees a pseudonymized profile, and every view is a transfer event
        long transfersBefore = uniqueTransfers(admin);
        JsonNode listed = get("/api/client/projects/" + projectId + "/applications", client).ok().get(0);
        assertThat(listed.path("clientTotalUsd").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(listed.path("cvAvailable").asBoolean()).isFalse();
        JsonNode engineerView = listed.path("engineer");
        assertThat(engineerView.path("alias").asText()).startsWith("QE-");
        assertThat(engineerView.has("email")).isFalse();
        assertThat(engineerView.has("cvText")).isFalse();
        assertThat(engineerView.has("education")).isFalse();
        assertThat(engineerView.has("portfolioLinks")).isFalse();
        assertThat(listed.toString()).doesNotContain("@test.qiao").doesNotContain("Hangzhou");
        assertThat(uniqueTransfers(admin)).isEqualTo(transfersBefore + 1);

        // ---- full CV only with per-project consent
        get("/api/client/applications/" + applicationId + "/cv", client).expect(403, "CV_CONSENT_REQUIRED");
        post("/api/engineer/applications/" + applicationId + "/cv-consent", engineer, Map.of("granted", true)).ok();
        JsonNode cv = get("/api/client/applications/" + applicationId + "/cv", client).ok();
        assertThat(cv.path("cvText").asText()).contains("Hangzhou");
        JsonNode transfers = get("/api/admin/transfers/summary", admin).ok();
        assertThat(transfers.path("uniqueEngineers").path("FULL_CV").asLong()).isPositive();
        assertThat(transfers.path("thresholds").path("limit").asLong()).isEqualTo(100_000);

        // ---- hire
        post("/api/client/applications/" + applicationId + "/hire", client, null).expect(409, "INVALID_STATE");
        post("/api/client/applications/" + applicationId + "/shortlist", client, null).ok();
        post("/api/client/applications/" + applicationId + "/select", client, null).ok();
        JsonNode contract = post("/api/client/applications/" + applicationId + "/hire", client, null).ok();
        long contractId = contract.path("id").asLong();
        assertThat(contract.path("status").asText()).isEqualTo("PENDING_SIGNATURES");
        // Each party sees only its own document: no client price or partner data leaks across.
        assertThat(contract.path("documents")).hasSize(1);
        assertThat(contract.path("milestones")).hasSize(2);
        assertThat(contract.path("engineerAlias").asText()).isEqualTo(engineerView.path("alias").asText());
        assertThat(get("/api/client/projects/" + projectId, client).ok().path("status").asText()).isEqualTo("IN_CONTRACT");

        long clientDocId = documentId(contract, "CLIENT_SERVICES");
        JsonNode engineerContract = get("/api/engineer/contracts/" + contractId, engineer).ok();
        assertThat(engineerContract.path("documents")).hasSize(1);
        assertThat(engineerContract.toString()).doesNotContain("2500.00");
        long engineerDocId = documentId(engineerContract, "ENGINEER_DELIVERABLES");
        JsonNode clientDoc = document(contract, "CLIENT_SERVICES");
        assertThat(clientDoc.path("body").asText()).contains("Acme Robotics").contains("Inventory dashboard")
                .contains("1000.00").contains("2500.00").doesNotContain("{{");
        assertThat(clientDoc.path("sha256").asText()).hasSize(64);

        // ---- documents
        post("/api/client/contracts/" + contractId + "/documents/" + engineerDocId + "/accept", client, null)
                .expect(404, "NOT_FOUND");
        assertThat(post("/api/client/contracts/" + contractId + "/documents/" + clientDocId + "/accept", client, null)
                .ok().path("status").asText()).isEqualTo("PENDING_SIGNATURES");
        JsonNode engineerDoc = document(engineerContract, "ENGINEER_DELIVERABLES");
        assertThat(engineerDoc.path("locale").asText()).isEqualTo("zh-CN");
        assertThat(engineerDoc.path("body").asText()).contains("没有固定工作时间").contains("PR-");
        JsonNode active = post("/api/engineer/contracts/" + contractId + "/documents/" + engineerDocId + "/accept",
                engineer, null).ok();
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");

        // ---- account deletion is refused with an active contract
        post("/api/me/delete", engineer, null).expect(409, "ACCOUNT_HAS_ACTIVE_CONTRACTS");

        // ---- fund, deliver and accept both milestones
        List<Long> milestoneIds = new ArrayList<>();
        active.path("milestones").forEach(m -> milestoneIds.add(m.path("id").asLong()));
        assertThat(active.path("milestones").get(0).path("talentAmountUsd").decimalValue()).isEqualByComparingTo("800.00");

        post("/api/engineer/milestones/" + milestoneIds.get(0) + "/submit", engineer, Map.of("note", "early"))
                .expect(409, "MILESTONE_NOT_FUNDED");
        for (long milestoneId : milestoneIds) {
            fund(client, milestoneId);
        }
        post("/api/client/milestones/" + milestoneIds.get(0) + "/checkout", client, null).expect(409, "PAYMENT_ALREADY_PAID");

        long attachmentId = upload(engineer, contractId, milestoneIds.get(0));
        var download = mvc.perform(MockMvcRequestBuilders.get("/api/attachments/" + attachmentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + client)).andReturn().getResponse();
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(download.getContentType()).isEqualTo("application/pdf");

        JsonNode submitted = post("/api/engineer/milestones/" + milestoneIds.get(0) + "/submit", engineer,
                Map.of("note", "API deployed to staging", "attachmentIds", List.of(attachmentId))).ok();
        assertThat(submitted.path("status").asText()).isEqualTo("SUBMITTED");
        post("/api/client/milestones/" + milestoneIds.get(0) + "/request-revision", client,
                Map.of("reason", "Please add pagination")).ok();
        JsonNode revised = get("/api/engineer/contracts/" + contractId, engineer).ok().path("milestones").get(0);
        assertThat(revised.path("status").asText()).isEqualTo("REVISION_REQUESTED");
        assertThat(revised.path("revisionReason").asText()).isEqualTo("Please add pagination");
        assertThat(get("/api/engineer/contracts", engineer).ok().get(0).path("milestones")).hasSize(2);
        post("/api/engineer/milestones/" + milestoneIds.get(0) + "/submit", engineer, Map.of("note", "Pagination added")).ok();
        assertThat(post("/api/client/milestones/" + milestoneIds.get(0) + "/accept", client, null).ok()
                .path("status").asText()).isEqualTo("ACCEPTED");

        post("/api/contracts/" + contractId + "/messages", client, Map.of("body", "Great work, thanks!")).ok();
        JsonNode messages = get("/api/contracts/" + contractId + "/messages", engineer).ok();
        assertThat(messages.get(0).path("senderAlias").asText()).isEqualTo("Acme Robotics");
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("attachments").get(0)
                .path("milestoneId").asLong()).isEqualTo(milestoneIds.get(0));

        post("/api/engineer/milestones/" + milestoneIds.get(1) + "/submit", engineer, Map.of("note", "Front end done")).ok();
        post("/api/client/milestones/" + milestoneIds.get(1) + "/accept", client, null).ok();
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("status").asText()).isEqualTo("COMPLETED");
        assertThat(get("/api/client/projects/" + projectId, client).ok().path("status").asText()).isEqualTo("COMPLETED");

        // ---- monthly settlement, CSV and partner payout
        JsonNode batch = post("/api/admin/settlements", admin, Map.of("period", "2026-11")).ok();
        long batchId = batch.path("id").asLong();
        assertThat(batch.path("status").asText()).isEqualTo("DRAFT");
        List<Long> settled = new ArrayList<>();
        batch.path("lines").forEach(line -> settled.add(line.path("contractMilestoneId").asLong()));
        assertThat(settled).containsAll(milestoneIds);
        post("/api/admin/settlements", admin, Map.of("period", "2026-11")).expect(409, "NOTHING_TO_SETTLE");

        var csv = mvc.perform(MockMvcRequestBuilders.get("/api/admin/settlements/" + batchId + "/export.csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)).andReturn().getResponse();
        String csvBody = csv.getContentAsString(StandardCharsets.UTF_8);
        assertThat(csvBody).contains("line_id,alias,partner_ref,engineer_email,contract_milestone_id,project_title,talent_amount_usd")
                .contains(engineerView.path("alias").asText()).contains("Inventory dashboard").contains("800.00");

        get("/api/partner/settlements/" + batchId, null).expect(401, "UNAUTHORIZED");
        partner(MockMvcRequestBuilders.get("/api/partner/settlements/" + batchId)).expect(404, "NOT_FOUND");
        post("/api/admin/settlements/" + batchId + "/mark-exported", admin, null).ok();
        JsonNode exported = partner(MockMvcRequestBuilders.get("/api/partner/settlements?status=EXPORTED")).ok();
        assertThat(exported.toString()).contains("\"id\":" + batchId);
        JsonNode partnerBatch = partner(MockMvcRequestBuilders.get("/api/partner/settlements/" + batchId)).ok();
        assertThat(partnerBatch.path("lines").get(0).path("engineerEmail").asText()).contains("@");
        for (JsonNode line : partnerBatch.path("lines")) {
            partner(MockMvcRequestBuilders.post("/api/partner/settlements/" + batchId + "/lines/" + line.path("lineId").asLong() + "/paid"),
                    Map.of("partnerPaymentRef", "BANK-" + line.path("lineId").asLong(),
                            "iitWithheldCny", new BigDecimal("120.00"), "netPaidCny", new BigDecimal("5000.00"),
                            "paidAt", "2026-12-05T08:00:00Z")).ok();
        }
        assertThat(get("/api/admin/settlements/" + batchId, admin).ok().path("status").asText()).isEqualTo("PAID");
        JsonNode earnings = get("/api/engineer/earnings", engineer).ok();
        assertThat(earnings).hasSize(2);
        earnings.forEach(e -> assertThat(e.path("settlementStatus").asText()).isEqualTo("PAID"));
        assertThat(get("/api/admin/audit?action=PARTNER_LINE_PAID", admin).ok().path("total").asLong()).isPositive();

        // ---- once the contract is completed the engineer can delete the account
        assertThat(post("/api/me/delete", engineer, null).ok().path("deleted").asBoolean()).isTrue();
        get("/api/me", engineer).expect(401, "UNAUTHORIZED");
    }

    private long uniqueTransfers(String admin) {
        return get("/api/admin/transfers/summary", admin).ok().path("uniqueEngineers").path("ANY").asLong();
    }

    private static JsonNode document(JsonNode contract, String kind) {
        for (JsonNode doc : contract.path("documents")) {
            if (doc.path("kind").asText().equals(kind)) {
                return doc;
            }
        }
        throw new AssertionError("No " + kind + " document");
    }

    private static long documentId(JsonNode contract, String kind) {
        return document(contract, kind).path("id").asLong();
    }

    private void fund(String client, long milestoneId) {
        JsonNode checkout = post("/api/client/milestones/" + milestoneId + "/checkout", client, null).ok();
        long paymentId = checkout.path("paymentId").asLong();
        assertThat(checkout.path("url").asText()).isEqualTo("http://localhost:5173/pay/fake/" + paymentId);
        JsonNode paid = post("/api/payments/fake/" + paymentId + "/confirm", client, null).ok();
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("contractId").asLong()).isPositive();
        post("/api/payments/fake/" + paymentId + "/confirm", client, null).expect(409, "PAYMENT_ALREADY_PAID");
    }

    private long upload(String token, long contractId, long milestoneId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "../report.pdf", "text/html",
                "%PDF-1.4 deliverable".getBytes(StandardCharsets.US_ASCII));
        var response = mvc.perform(multipart("/api/contracts/" + contractId + "/attachments")
                        .file(file)
                        .param("milestoneId", String.valueOf(milestoneId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(body.path("data").path("filename").asText()).isEqualTo("report.pdf");
        assertThat(body.path("data").path("contentType").asText()).isEqualTo("application/pdf");

        MockMultipartFile executable = new MockMultipartFile("file", "tool.exe", "application/pdf",
                new byte[]{'M', 'Z', 0, 0});
        var rejected = mvc.perform(multipart("/api/contracts/" + contractId + "/attachments").file(executable)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
        assertThat(rejected.getContentAsString()).contains("UNSUPPORTED_FILE_TYPE");

        MockMultipartFile oversized = new MockMultipartFile("file", "big.bin", "application/octet-stream",
                new byte[20 * 1024 * 1024 + 1]);
        var tooLarge = mvc.perform(multipart("/api/contracts/" + contractId + "/attachments").file(oversized)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
        assertThat(tooLarge.getStatus()).isEqualTo(413);
        assertThat(tooLarge.getContentAsString()).contains("\"code\":\"FILE_TOO_LARGE\"");
        return body.path("data").path("id").asLong();
    }

    private Response partner(MockHttpServletRequestBuilder request) {
        return partner(request, null);
    }

    private Response partner(MockHttpServletRequestBuilder request,
                             Object body) {
        request.header("X-Partner-Key", PARTNER_KEY);
        return send(request, null, body);
    }
}
