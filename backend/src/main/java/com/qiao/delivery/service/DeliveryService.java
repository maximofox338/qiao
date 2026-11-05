package com.qiao.delivery.service;

import com.qiao.contract.domain.ContractMilestone;
import com.qiao.contract.service.MilestoneRef;
import com.qiao.contract.service.ContractService;
import com.qiao.contract.service.MilestoneService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Engineer delivery of a milestone (the lead in a team contract): submission note plus files previously uploaded by
 * the engineer side.
 */
@Service
public class DeliveryService {

    private final MilestoneService milestoneService;
    private final ContractService contractService;
    private final AttachmentService attachmentService;

    public DeliveryService(MilestoneService milestoneService, ContractService contractService,
                           AttachmentService attachmentService) {
        this.milestoneService = milestoneService;
        this.contractService = contractService;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public ContractMilestone submit(long engineerUserId, long milestoneId, String note, List<Long> attachmentIds) {
        ContractMilestone submitted = milestoneService.submit(engineerUserId, milestoneId, note);
        MilestoneRef ref = milestoneService.ref(milestoneId);
        attachmentService.linkToMilestone(attachmentIds == null ? List.of() : attachmentIds, ref.contractId(),
                milestoneId, contractService.engineerUserIds(contractService.get(ref.contractId())));
        return submitted;
    }
}
