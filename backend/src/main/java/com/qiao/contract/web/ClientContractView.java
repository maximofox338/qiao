package com.qiao.contract.web;

import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.service.AttachmentSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contract as the client sees it. {@code documents} holds only the client's own CLIENT_SERVICES document; the
 * engineer's acceptance is exposed as a timestamp. List views omit documents and attachments.
 */
public record ClientContractView(Long id, Long projectId, String projectTitle, ContractStatus status,
                                 String engineerAlias, BigDecimal clientTotalUsd, Instant clientAcceptedAt,
                                 Instant engineerAcceptedAt, Instant activatedAt, Instant completedAt,
                                 Instant createdAt, List<ContractDocumentView> documents,
                                 List<ClientMilestoneView> milestones, List<AttachmentSummary> attachments) {
}
