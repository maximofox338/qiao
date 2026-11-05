package com.qiao.contract.web;

import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.service.AttachmentSummary;
import com.qiao.contract.service.ReviewSummary;
import com.qiao.team.domain.TeamRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contract as the client sees it. {@code documents} holds only the client's own CLIENT_SERVICES document; the
 * engineer's acceptance is exposed as a timestamp (for a team: when the last member accepted, with each member's
 * acceptance in {@code team}). List views omit documents and attachments. {@code myReview} is the client side's
 * review once the contract is COMPLETED; {@code canReview} tells whether it can still be written.
 */
public record ClientContractView(Long id, Long projectId, String projectTitle, ContractStatus status,
                                 String engineerAlias, BigDecimal clientTotalUsd, Instant clientAcceptedAt,
                                 Instant engineerAcceptedAt, Instant activatedAt, Instant completedAt,
                                 Instant createdAt, List<ContractDocumentView> documents,
                                 List<ClientMilestoneView> milestones, List<AttachmentSummary> attachments,
                                 Team team, ReviewSummary myReview, boolean canReview) {

    public record Team(String name, List<Member> members) {
    }

    public record Member(String alias, TeamRole role, boolean accepted, Instant acceptedAt) {
    }
}
