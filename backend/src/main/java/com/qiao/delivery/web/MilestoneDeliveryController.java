package com.qiao.delivery.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.contract.service.ContractService;
import com.qiao.contract.service.MilestoneService;
import com.qiao.contract.web.ClientMilestoneView;
import com.qiao.contract.web.EngineerMilestoneView;
import com.qiao.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MilestoneDeliveryController {

    private final DeliveryService deliveryService;
    private final MilestoneService milestoneService;
    private final ContractService contractService;

    public MilestoneDeliveryController(DeliveryService deliveryService, MilestoneService milestoneService,
                                       ContractService contractService) {
        this.deliveryService = deliveryService;
        this.milestoneService = milestoneService;
        this.contractService = contractService;
    }

    @PostMapping("/api/engineer/milestones/{id}/submit")
    public ApiResponse<EngineerMilestoneView> submit(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable long id,
                                                     @Valid @RequestBody SubmitMilestoneRequest request) {
        return ApiResponse.ok(contractService.engineerMilestoneView(principal.id(),
                deliveryService.submit(principal.id(), id, request.note(), request.attachmentIds())));
    }

    @PostMapping("/api/client/milestones/{id}/accept")
    public ApiResponse<ClientMilestoneView> accept(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(ClientMilestoneView.from(milestoneService.accept(principal.id(), id)));
    }

    @PostMapping("/api/client/milestones/{id}/request-revision")
    public ApiResponse<ClientMilestoneView> requestRevision(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable long id,
                                                            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(ClientMilestoneView.from(milestoneService.requestRevision(principal.id(), id, request.reason())));
    }

    @PostMapping("/api/client/milestones/{id}/dispute")
    public ApiResponse<ClientMilestoneView> dispute(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(ClientMilestoneView.from(milestoneService.dispute(principal.id(), id, request.reason())));
    }
}
