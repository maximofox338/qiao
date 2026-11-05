package com.qiao.delivery.service;

import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.service.MilestoneRef;
import com.qiao.contract.service.MilestoneService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Engineer delivery of a milestone: submission note plus previously uploaded files.
 */
@Service
public class DeliveryService {

    private final MilestoneService milestoneService;
    private final AttachmentService attachmentService;

    public DeliveryService(MilestoneService milestoneService, AttachmentService attachmentService) {
        this.milestoneService = milestoneService;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public ContractMilestone submit(long engineerUserId, long milestoneId, String note, List<Long> attachmentIds) {
        ContractMilestone submitted = milestoneService.submit(engineerUserId, milestoneId, note);
        MilestoneRef ref = milestoneService.ref(milestoneId);
        attachmentService.linkToMilestone(attachmentIds == null ? List.of() : attachmentIds, ref.contractId(),
                milestoneId, engineerUserId);
        return submitted;
    }
}
