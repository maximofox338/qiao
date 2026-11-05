package com.qiao.contract.web;

import com.qiao.contract.domain.ContractStatus;
import com.qiao.contract.service.AttachmentSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contract as the engineer sees it: talent amounts only, client identified by country. {@code documents} holds only
 * the engineer's own ENGINEER_DELIVERABLES document; list views omit documents and attachments.
 */
public record EngineerContractView(Long id, Long projectId, String projectTitle, ContractStatus status,
                                   String engineerAlias, String clientCountry, BigDecimal talentTotalUsd, Instant clientAcceptedAt,
                                   Instant engineerAcceptedAt, Instant activatedAt, Instant completedAt,
                                   Instant createdAt, List<ContractDocumentView> documents,
                                   List<EngineerMilestoneView> milestones, List<AttachmentSummary> attachments) {
}
