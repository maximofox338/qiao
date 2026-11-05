package com.qiao.contract.web;

import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.service.AttachmentSummary;
import com.qiao.contract.service.ReviewSummary;
import com.qiao.team.domain.TeamRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contract as an engineer sees it: talent amounts only, client identified by country. {@code documents} holds only
 * the viewer's own ENGINEER_DELIVERABLES document; list views omit documents and attachments. Team contracts carry
 * {@code team} (members by alias with their shares and acceptance) and the viewer's own amounts. {@code myReview}
 * is the engineer side's review of the client (written by the lead in a team); {@code canReview} is true only for
 * the engineer or lead while it is missing on a COMPLETED contract.
 */
public record EngineerContractView(Long id, Long projectId, String projectTitle, ContractStatus status,
                                   String engineerAlias, String clientCountry, BigDecimal talentTotalUsd, Instant clientAcceptedAt,
                                   Instant engineerAcceptedAt, Instant activatedAt, Instant completedAt,
                                   Instant createdAt, List<ContractDocumentView> documents,
                                   List<EngineerMilestoneView> milestones, List<AttachmentSummary> attachments,
                                   Team team, ReviewSummary myReview, boolean canReview) {

    public record Team(Long id, String name, TeamRole myRole, BigDecimal mySharePct, BigDecimal myTalentTotalUsd,
                       List<Member> members) {
    }

    public record Member(String alias, TeamRole role, BigDecimal sharePct, boolean accepted) {
    }
}
