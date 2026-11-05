package com.qiao.contract.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.contract.service.ContractService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/engineer/contracts")
public class EngineerContractController {

    private final ContractService contractService;

    public EngineerContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public ApiResponse<List<EngineerContractView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(contractService.listForEngineer(principal.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<EngineerContractView> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(contractService.getForEngineer(principal.id(), id));
    }

    @PostMapping("/{id}/documents/{docId}/accept")
    public ApiResponse<EngineerContractView> acceptDocument(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable long id, @PathVariable long docId) {
        contractService.acceptDocument(principal, id, docId);
        return ApiResponse.ok(contractService.getForEngineer(principal.id(), id));
    }
}
