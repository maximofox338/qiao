package com.qiao.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.compliance.domain.TransferDataKind;
import com.qiao.compliance.domain.TransferEvent;
import com.qiao.compliance.domain.TransferEventMapper;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Teams (spec §12): invitation → team application with a split → team card for the client → per-member CV consent →
 * hire with one agreement per member → delivery by the lead → one settlement line per member.
 */
class TeamFlowIT extends IntegrationTest {

    private record Engineer(String token, long userId, String email, String alias, String partnerRef, String cvText) {
    }

    @Autowired
    private TransferEventMapper transferEventMapper;

    @Test
    void teamGoesFromInvitationToSettlement() throws Exception {
        String client = approvedClient();
        String admin = adminToken();
        long projectId = openProject(client);
        Engineer lead = engineer("lead");
        Engineer member = engineer("member");
        Engineer outsider = engineer("outsider");

        // ---- team creation and invitations
        String unapproved = register(uniqueEmail("engineer"), "ENGINEER");
        post("/api/engineer/teams", unapproved, Map.of("name", "Nope")).expect(409, "PROFILE_NOT_APPROVED");
        JsonNode team = post("/api/engineer/teams", lead.token(), Map.of("name", "Alpha Squad",
                "description", "Vue + Spring Boot, two engineers")).ok();
        long teamId = team.path("id").asLong();
        assertThat(team.path("myRole").asText()).isEqualTo("LEAD");
        assertThat(team.path("canApply").asBoolean()).isFalse();

        post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", "QE-NOPE"))
                .expect(404, "ENGINEER_NOT_FOUND");
        String unapprovedAlias = get("/api/engineer/profile", unapproved).ok().path("alias").asText();
        post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", unapprovedAlias))
                .expect(404, "ENGINEER_NOT_FOUND");
        post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", member.alias().toLowerCase())).ok();
        post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", member.alias()))
                .expect(409, "ALREADY_TEAM_MEMBER");
        get("/api/engineer/teams/" + teamId, outsider.token()).expect(404, "NOT_FOUND");

        JsonNode invitations = get("/api/engineer/team-invitations", member.token()).ok();
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).path("teamName").asText()).isEqualTo("Alpha Squad");
        assertThat(invitations.get(0).path("leadAlias").asText()).isEqualTo(lead.alias());
        assertThat(get("/api/engineer/teams/" + teamId, member.token()).ok().path("myStatus").asText()).isEqualTo("INVITED");
        assertThat(get("/api/admin/outbox?size=100", admin).ok().toString()).contains("Alpha Squad");

        JsonNode joined = post("/api/engineer/teams/" + teamId + "/invitations/accept", member.token(), null).ok();
        assertThat(joined.path("activeMembers").asInt()).isEqualTo(2);
        assertThat(joined.path("myRole").asText()).isEqualTo("MEMBER");
        assertThat(get("/api/engineer/teams", lead.token()).ok().get(0).path("canApply").asBoolean()).isTrue();
        post("/api/engineer/teams/" + teamId + "/invitations", member.token(), Map.of("alias", outsider.alias()))
                .expect(403, "TEAM_LEAD_ONLY");
        // teammates see alias, headline and skills only
        String teamJson = get("/api/engineer/teams/" + teamId, member.token()).ok().toString();
        assertThat(teamJson).contains(lead.alias()).doesNotContain(lead.email()).doesNotContain(lead.partnerRef())
                .doesNotContain(lead.cvText()).doesNotContain("@test.qiao");

        // ---- team application with a split
        JsonNode project = get("/api/engineer/projects/" + projectId, lead.token()).ok();
        List<Long> projectMilestones = new ArrayList<>();
        project.path("milestones").forEach(m -> projectMilestones.add(m.path("id").asLong()));
        List<Map<String, Object>> prices = List.of(
                Map.of("milestoneId", projectMilestones.get(0), "talentAmountUsd", new BigDecimal("800.01")),
                Map.of("milestoneId", projectMilestones.get(1), "talentAmountUsd", new BigDecimal("1200.00")));
        String path = "/api/engineer/projects/" + projectId + "/team-applications";
        Map<String, Object> body = new HashMap<>(Map.of("teamId", teamId, "coverNote", "Two of us, one per milestone.",
                "prices", prices, "shares", List.of(share(lead, "60"), share(member, "40"))));

        post(path, member.token(), body).expect(403, "TEAM_LEAD_ONLY");
        post(path, lead.token(), with(body, "shares", List.of(share(lead, "60"), share(member, "30"))))
                .expect(400, "SHARES_SUM_MISMATCH");
        post(path, lead.token(), with(body, "shares", List.of(share(lead, "100"))))
                .expect(400, "VALIDATION_ERROR");
        post(path, lead.token(), with(body, "shares", List.of(share(lead, "60"), share(outsider, "40"))))
                .expect(400, "VALIDATION_ERROR");
        post("/api/me/consents", member.token(), Map.of("type", "SHARE_PROFILE_PSEUDONYMIZED", "granted", false)).ok();
        Response missingConsent = post(path, lead.token(), body).expect(409, "CONSENT_REQUIRED");
        assertThat(missingConsent.data().path("aliases").toString()).contains(member.alias()).doesNotContain(lead.alias());
        grantConsent(member.token(), "SHARE_PROFILE_PSEUDONYMIZED");

        JsonNode applied = post(path, lead.token(), body).ok();
        long applicationId = applied.path("id").asLong();
        assertThat(applied.path("talentTotalUsd").decimalValue()).isEqualByComparingTo("2000.01");
        assertThat(applied.path("team").path("myRole").asText()).isEqualTo("LEAD");
        assertThat(applied.path("team").path("members")).hasSize(2);

        // one application per engineer and project, alone or in a team
        post("/api/engineer/projects/" + projectId + "/applications", member.token(),
                Map.of("coverNote", "solo", "prices", prices)).expect(409, "ALREADY_APPLIED");
        post("/api/engineer/projects/" + projectId + "/applications", lead.token(),
                Map.of("coverNote", "solo", "prices", prices)).expect(409, "ALREADY_APPLIED");
        assertThat(get("/api/engineer/projects/" + projectId, member.token()).ok().path("applied").asBoolean()).isTrue();
        JsonNode memberApplication = get("/api/engineer/applications", member.token()).ok().get(0);
        assertThat(memberApplication.path("id").asLong()).isEqualTo(applicationId);
        assertThat(memberApplication.path("team").path("myRole").asText()).isEqualTo("MEMBER");
        assertThat(memberApplication.path("team").path("mySharePct").decimalValue()).isEqualByComparingTo("40");
        post("/api/engineer/applications/" + applicationId + "/withdraw", member.token(), null).expect(403, "TEAM_LEAD_ONLY");
        post("/api/engineer/applications/" + applicationId + "/withdraw", outsider.token(), null).expect(404, "NOT_FOUND");

        // a committed member cannot leave or be removed, and the team cannot be dissolved
        long memberRowId = memberRowId(lead.token(), teamId, member.alias());
        post("/api/engineer/teams/" + teamId + "/leave", member.token(), null).expect(409, "TEAM_HAS_ACTIVE_WORK");
        post("/api/engineer/teams/" + teamId + "/members/" + memberRowId + "/remove", lead.token(), null)
                .expect(409, "TEAM_HAS_ACTIVE_WORK");
        post("/api/engineer/teams/" + teamId + "/dissolve", lead.token(), null).expect(409, "TEAM_HAS_ACTIVE_WORK");

        // ---- the client sees a team card of pseudonymized profiles; one transfer event per member
        long transfersBefore = transfers(admin, "ANY");
        long leadEvents = profileEvents(lead);
        long memberEvents = profileEvents(member);
        JsonNode card = get("/api/client/projects/" + projectId + "/applications", client).ok().get(0);
        assertThat(transfers(admin, "ANY")).isEqualTo(transfersBefore + 2);
        assertThat(profileEvents(lead)).isEqualTo(leadEvents + 1);
        assertThat(profileEvents(member)).isEqualTo(memberEvents + 1);
        assertThat(card.path("team").path("name").asText()).isEqualTo("Alpha Squad");
        assertThat(card.path("team").path("members")).hasSize(2);
        assertThat(card.path("team").path("members").get(0).path("alias").asText()).isEqualTo(lead.alias());
        assertThat(card.path("team").path("members").get(0).path("role").asText()).isEqualTo("LEAD");
        assertThat(card.path("team").path("members").get(1).path("profile").path("headline").asText()).isNotBlank();
        assertThat(card.path("engineer").path("alias").asText()).isEqualTo(lead.alias());
        assertThat(card.path("clientTotalUsd").decimalValue()).isEqualByComparingTo("2500.01");
        String cardJson = card.toString();
        assertThat(cardJson).doesNotContain(lead.email()).doesNotContain(member.email())
                .doesNotContain(lead.partnerRef()).doesNotContain(member.partnerRef())
                .doesNotContain(lead.cvText()).doesNotContain(member.cvText())
                .doesNotContain("sharePct").doesNotContain("portfolio");

        // ---- full CV per member, only with that member's consent for the project
        get("/api/client/applications/" + applicationId + "/cv", client).expect(400, "VALIDATION_ERROR");
        get("/api/client/applications/" + applicationId + "/cv?alias=" + member.alias(), client).expect(403, "CV_CONSENT_REQUIRED");
        post("/api/engineer/applications/" + applicationId + "/cv-consent", member.token(), Map.of("granted", true)).ok();
        long fullCvBefore = transfers(admin, "FULL_CV");
        JsonNode cv = get("/api/client/applications/" + applicationId + "/cv?alias=" + member.alias(), client).ok();
        assertThat(cv.path("cvText").asText()).isEqualTo(member.cvText());
        assertThat(transfers(admin, "FULL_CV")).isEqualTo(fullCvBefore + 1);
        get("/api/client/applications/" + applicationId + "/cv?alias=" + lead.alias(), client).expect(403, "CV_CONSENT_REQUIRED");
        get("/api/client/applications/" + applicationId + "/cv?alias=" + outsider.alias(), client).expect(404, "NOT_FOUND");
        JsonNode cvFlags = get("/api/client/projects/" + projectId + "/applications", client).ok().get(0).path("team").path("members");
        assertThat(cvFlags.get(0).path("cvAvailable").asBoolean()).isFalse();
        assertThat(cvFlags.get(1).path("cvAvailable").asBoolean()).isTrue();
        assertThat(profileEvents(member)).isEqualTo(memberEvents + 2);
        assertThat(transferEventMapper.selectCount(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getEngineerUserId, lead.userId())
                .eq(TransferEvent::getDataKind, TransferDataKind.FULL_CV))).isZero();

        // ---- hire: one contract, one deliverables agreement per member
        post("/api/client/applications/" + applicationId + "/select", client, null).ok();
        JsonNode contract = post("/api/client/applications/" + applicationId + "/hire", client, null).ok();
        long contractId = contract.path("id").asLong();
        assertThat(contract.path("documents")).hasSize(1);
        assertThat(contract.path("team").path("name").asText()).isEqualTo("Alpha Squad");
        assertThat(contract.path("team").path("members")).hasSize(2);
        assertThat(contract.path("team").toString()).doesNotContain("sharePct");
        JsonNode clientDoc = contract.path("documents").get(0);
        assertThat(clientDoc.path("body").asText()).contains(lead.alias() + ", " + member.alias())
                .contains("Alpha Squad").contains("2500.01").doesNotContain(lead.partnerRef()).doesNotContain(member.partnerRef());

        JsonNode leadContract = get("/api/engineer/contracts/" + contractId, lead.token()).ok();
        JsonNode memberContract = get("/api/engineer/contracts/" + contractId, member.token()).ok();
        get("/api/engineer/contracts/" + contractId, outsider.token()).expect(404, "NOT_FOUND");
        assertThat(leadContract.path("documents")).hasSize(1);
        assertThat(memberContract.path("documents")).hasSize(1);
        JsonNode leadDoc = leadContract.path("documents").get(0);
        JsonNode memberDoc = memberContract.path("documents").get(0);
        assertThat(leadDoc.path("id").asLong()).isNotEqualTo(memberDoc.path("id").asLong());
        assertThat(leadDoc.path("body").asText()).contains(lead.alias()).contains(lead.partnerRef()).contains("60.00")
                .contains("480.01").contains("1200.01").doesNotContain(member.partnerRef());
        assertThat(memberDoc.path("body").asText()).contains(member.alias()).contains(member.partnerRef()).contains("40.00")
                .contains("320.00").contains("800.00").doesNotContain(lead.partnerRef());
        for (JsonNode view : List.of(leadContract, memberContract)) {
            assertThat(view.toString()).doesNotContain("Acme Robotics").doesNotContain("2500.01").doesNotContain("1000.01")
                    .doesNotContain(lead.email()).doesNotContain(member.email()).doesNotContain(lead.cvText());
        }
        assertThat(memberContract.toString()).doesNotContain(lead.partnerRef());
        assertThat(leadContract.toString()).doesNotContain(member.partnerRef());
        assertThat(memberContract.path("team").path("myRole").asText()).isEqualTo("MEMBER");
        assertThat(memberContract.path("team").path("mySharePct").decimalValue()).isEqualByComparingTo("40");
        assertThat(memberContract.path("team").path("myTalentTotalUsd").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(memberContract.path("milestones").get(0).path("myAmountUsd").decimalValue()).isEqualByComparingTo("320.00");
        assertThat(leadContract.path("milestones").get(0).path("myAmountUsd").decimalValue()).isEqualByComparingTo("480.01");
        assertThat(get("/api/engineer/contracts", member.token()).ok().get(0).path("id").asLong()).isEqualTo(contractId);

        // ---- each member accepts their own document; ACTIVE once the client and all members accepted
        post("/api/engineer/contracts/" + contractId + "/documents/" + leadDoc.path("id").asLong() + "/accept",
                member.token(), null).expect(404, "NOT_FOUND");
        post("/api/client/contracts/" + contractId + "/documents/" + clientDoc.path("id").asLong() + "/accept", client, null).ok();
        JsonNode afterLead = post("/api/engineer/contracts/" + contractId + "/documents/" + leadDoc.path("id").asLong()
                + "/accept", lead.token(), null).ok();
        assertThat(afterLead.path("status").asText()).isEqualTo("PENDING_SIGNATURES");
        assertThat(afterLead.path("engineerAcceptedAt").isNull()).isTrue();
        JsonNode active = post("/api/engineer/contracts/" + contractId + "/documents/" + memberDoc.path("id").asLong()
                + "/accept", member.token(), null).ok();
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");
        JsonNode clientActive = get("/api/client/contracts/" + contractId, client).ok();
        assertThat(clientActive.path("engineerAcceptedAt").isNull()).isFalse();
        clientActive.path("team").path("members").forEach(m -> assertThat(m.path("accepted").asBoolean()).isTrue());

        // ---- delivery: only the lead submits; files and messages are shared with every member
        List<Long> milestoneIds = new ArrayList<>();
        active.path("milestones").forEach(m -> milestoneIds.add(m.path("id").asLong()));
        for (long milestoneId : milestoneIds) {
            long paymentId = post("/api/client/milestones/" + milestoneId + "/checkout", client, null).ok().path("paymentId").asLong();
            post("/api/payments/fake/" + paymentId + "/confirm", client, null).ok();
        }
        post("/api/engineer/milestones/" + milestoneIds.get(0) + "/submit", member.token(), Map.of("note", "mine"))
                .expect(403, "TEAM_LEAD_ONLY");
        long memberFile = upload(member.token(), contractId);
        JsonNode submitted = post("/api/engineer/milestones/" + milestoneIds.get(0) + "/submit", lead.token(),
                Map.of("note", "Backend API on staging", "attachmentIds", List.of(memberFile))).ok();
        assertThat(submitted.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.path("myAmountUsd").decimalValue()).isEqualByComparingTo("480.01");
        post("/api/contracts/" + contractId + "/messages", member.token(), Map.of("body", "My part is in the zip.")).ok();
        post("/api/contracts/" + contractId + "/messages", lead.token(), Map.of("body", "Submitted milestone 1.")).ok();
        post("/api/contracts/" + contractId + "/messages", client, Map.of("body", "Thanks team!")).ok();
        get("/api/contracts/" + contractId + "/messages", outsider.token()).expect(404, "NOT_FOUND");
        JsonNode thread = get("/api/contracts/" + contractId + "/messages", member.token()).ok();
        assertThat(thread).hasSize(3);
        assertThat(thread.get(0).path("senderAlias").asText()).isEqualTo(member.alias());
        assertThat(thread.get(1).path("senderAlias").asText()).isEqualTo(lead.alias());
        assertThat(thread.get(2).path("senderSide").asText()).isEqualTo("CLIENT");
        assertThat(thread.get(2).path("senderAlias").isNull()).isTrue();
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("attachments").get(0).path("milestoneId").asLong())
                .isEqualTo(milestoneIds.get(0));

        post("/api/client/milestones/" + milestoneIds.get(0) + "/accept", client, null).ok();
        post("/api/engineer/milestones/" + milestoneIds.get(1) + "/submit", lead.token(), Map.of("note", "Front end")).ok();
        post("/api/client/milestones/" + milestoneIds.get(1) + "/accept", client, null).ok();
        assertThat(get("/api/client/contracts/" + contractId, client).ok().path("status").asText()).isEqualTo("COMPLETED");

        // ---- settlement: one line per member and milestone, with each member's share
        JsonNode batch = post("/api/admin/settlements", admin, Map.of("period", "2026-11")).ok();
        long batchId = batch.path("id").asLong();
        Map<String, BigDecimal> totals = new HashMap<>();
        int lines = 0;
        for (JsonNode line : batch.path("lines")) {
            if (milestoneIds.contains(line.path("contractMilestoneId").asLong())) {
                lines++;
                totals.merge(line.path("partnerRef").asText(), line.path("talentAmountUsd").decimalValue(), BigDecimal::add);
            }
        }
        assertThat(lines).isEqualTo(4);
        assertThat(totals.get(lead.partnerRef())).isEqualByComparingTo("1200.01");
        assertThat(totals.get(member.partnerRef())).isEqualByComparingTo("800.00");
        String csv = mvc.perform(MockMvcRequestBuilders.get("/api/admin/settlements/" + batchId + "/export.csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains(lead.partnerRef()).contains(member.partnerRef()).contains("320.00").contains("480.01");

        post("/api/admin/settlements/" + batchId + "/mark-exported", admin, null).ok();
        JsonNode partnerBatch = partner(MockMvcRequestBuilders.get("/api/partner/settlements/" + batchId), null).ok();
        for (JsonNode line : partnerBatch.path("lines")) {
            if (line.path("partnerRef").asText().equals(member.partnerRef())) {
                assertThat(line.path("engineerEmail").asText()).isEqualTo(member.email());
                assertThat(line.path("alias").asText()).isEqualTo(member.alias());
            }
            partner(MockMvcRequestBuilders.post("/api/partner/settlements/" + batchId + "/lines/" + line.path("lineId").asLong() + "/paid"),
                    Map.of("partnerPaymentRef", "BANK-" + line.path("lineId").asLong(), "iitWithheldCny", new BigDecimal("10.00"),
                            "netPaidCny", new BigDecimal("1000.00"), "paidAt", "2026-12-05T08:00:00Z")).ok();
        }
        JsonNode memberEarnings = get("/api/engineer/earnings", member.token()).ok();
        assertThat(memberEarnings).hasSize(2);
        BigDecimal earned = BigDecimal.ZERO;
        for (JsonNode e : memberEarnings) {
            assertThat(e.path("settlementStatus").asText()).isEqualTo("PAID");
            earned = earned.add(e.path("talentAmountUsd").decimalValue());
        }
        assertThat(earned).isEqualByComparingTo("800.00");

        // ---- the work is done: the member can leave now
        post("/api/engineer/teams/" + teamId + "/leave", member.token(), null).ok();
        assertThat(get("/api/engineer/teams/" + teamId, lead.token()).ok().path("activeMembers").asInt()).isEqualTo(1);
    }

    @Test
    void teamRulesAndIndividualApplicationsStillWork() {
        String client = approvedClient();
        long projectId = openProject(client);
        Engineer lead = engineer("lead");
        Engineer member = engineer("member");
        Engineer solo = engineer("solo");

        long teamId = post("/api/engineer/teams", lead.token(), Map.of("name", "Solo team")).ok().path("id").asLong();
        JsonNode project = get("/api/engineer/projects/" + projectId, lead.token()).ok();
        List<Map<String, Object>> prices = new ArrayList<>();
        project.path("milestones").forEach(m -> prices.add(Map.of("milestoneId", m.path("id").asLong(),
                "talentAmountUsd", m.path("talentAmountUsd").decimalValue())));
        post("/api/engineer/projects/" + projectId + "/team-applications", lead.token(), Map.of("teamId", teamId,
                "coverNote", "x", "prices", prices, "shares", List.of(share(lead, "100")))).expect(409, "TEAM_TOO_SMALL");

        // invitations can be declined, cancelled and sent again
        post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", member.alias())).ok();
        post("/api/engineer/teams/" + teamId + "/invitations/decline", member.token(), null).ok();
        assertThat(get("/api/engineer/team-invitations", member.token()).ok()).isEmpty();
        JsonNode reinvited = post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", member.alias())).ok();
        assertThat(reinvited.toString()).contains("INVITED");
        long invitationRow = memberRowId(lead.token(), teamId, member.alias());
        post("/api/engineer/teams/" + teamId + "/members/" + invitationRow + "/remove", lead.token(), null).ok();
        post("/api/engineer/teams/" + teamId + "/invitations/accept", member.token(), null).expect(404, "NOT_FOUND");

        // individual applications are unchanged and coexist with teams
        JsonNode individual = post("/api/engineer/projects/" + projectId + "/applications", solo.token(),
                Map.of("coverNote", "Solo bid", "prices", prices)).ok();
        assertThat(individual.path("team").isNull()).isTrue();
        JsonNode listed = get("/api/client/projects/" + projectId + "/applications", client).ok().get(0);
        assertThat(listed.path("team").isNull()).isTrue();
        assertThat(listed.path("engineer").path("alias").asText()).isEqualTo(solo.alias());
        get("/api/client/applications/" + listed.path("id").asLong() + "/cv", client).expect(403, "CV_CONSENT_REQUIRED");

        // dissolving a team with no active work
        post("/api/engineer/teams/" + teamId + "/dissolve", lead.token(), null).ok();
        get("/api/engineer/teams/" + teamId, lead.token()).expect(404, "NOT_FOUND");
        assertThat(get("/api/engineer/teams", lead.token()).ok()).isEmpty();
    }

    @Test
    void deletingAMemberAccountWithdrawsTheTeamApplication() {
        String client = approvedClient();
        long projectId = openProject(client);
        Engineer lead = engineer("lead");
        Engineer member = engineer("member");
        long teamId = post("/api/engineer/teams", lead.token(), Map.of("name", "Short lived")).ok().path("id").asLong();
        post("/api/engineer/teams/" + teamId + "/invitations", lead.token(), Map.of("alias", member.alias())).ok();
        post("/api/engineer/teams/" + teamId + "/invitations/accept", member.token(), null).ok();
        JsonNode project = get("/api/engineer/projects/" + projectId, lead.token()).ok();
        List<Map<String, Object>> prices = new ArrayList<>();
        project.path("milestones").forEach(m -> prices.add(Map.of("milestoneId", m.path("id").asLong(),
                "talentAmountUsd", m.path("talentAmountUsd").decimalValue())));
        long applicationId = post("/api/engineer/projects/" + projectId + "/team-applications", lead.token(),
                Map.of("teamId", teamId, "coverNote", "x", "prices", prices,
                        "shares", List.of(share(lead, "50"), share(member, "50")))).ok().path("id").asLong();

        assertThat(post("/api/me/delete", member.token(), null).ok().path("deleted").asBoolean()).isTrue();
        JsonNode own = get("/api/engineer/applications", lead.token()).ok().get(0);
        assertThat(own.path("id").asLong()).isEqualTo(applicationId);
        assertThat(own.path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(get("/api/engineer/teams/" + teamId, lead.token()).ok().path("activeMembers").asInt()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private Engineer engineer(String label) {
        String cvText = "CV of " + label + " " + UUID.randomUUID();
        Map<String, Object> profile = profileBody();
        profile.put("cvText", cvText);
        profile.put("headline", label + " engineer");
        String token = approvedEngineer(profile);
        JsonNode me = get("/api/me", token).ok();
        JsonNode p = get("/api/engineer/profile", token).ok();
        return new Engineer(token, me.path("id").asLong(), me.path("email").asText(), p.path("alias").asText(),
                "PR-" + p.path("id").asLong(), cvText);
    }

    private static Map<String, Object> share(Engineer engineer, String pct) {
        return Map.of("alias", engineer.alias(), "sharePct", new BigDecimal(pct));
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    private long memberRowId(String token, long teamId, String alias) {
        for (JsonNode m : get("/api/engineer/teams/" + teamId, token).ok().path("members")) {
            if (m.path("alias").asText().equals(alias)) {
                return m.path("id").asLong();
            }
        }
        throw new AssertionError("No member " + alias);
    }

    private long transfers(String admin, String kind) {
        return get("/api/admin/transfers/summary", admin).ok().path("uniqueEngineers").path(kind).asLong();
    }

    private long profileEvents(Engineer engineer) {
        return transferEventMapper.selectCount(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getEngineerUserId, engineer.userId())
                .eq(TransferEvent::getDataKind, TransferDataKind.PSEUDONYMIZED_PROFILE));
    }

    private long upload(String token, long contractId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "part.pdf", "application/pdf",
                "%PDF-1.4 member part".getBytes(StandardCharsets.US_ASCII));
        var response = mvc.perform(multipart("/api/contracts/" + contractId + "/attachments").file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asText()).isEqualTo("OK");
        return body.path("data").path("id").asLong();
    }

    private Response partner(MockHttpServletRequestBuilder request, Object body) {
        request.header("X-Partner-Key", PARTNER_KEY);
        return send(request, null, body);
    }
}
