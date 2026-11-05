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
@RequestMapping("/api/client")
public class ClientContractController {

    private final ContractService contractService;

    public ClientContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping("/applications/{id}/hire")
    public ApiResponse<ClientContractView> hire(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(contractService.hire(principal.id(), id));
    }

    @GetMapping("/contracts")
    public ApiResponse<List<ClientContractView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(contractService.listForClient(principal.id()));
    }

    @GetMapping("/contracts/{id}")
    public ApiResponse<ClientContractView> get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(contractService.getForClient(principal.id(), id));
    }

    @PostMapping("/contracts/{id}/documents/{docId}/accept")
    public ApiResponse<ClientContractView> acceptDocument(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable long id, @PathVariable long docId) {
        contractService.acceptDocument(principal, id, docId);
        return ApiResponse.ok(contractService.getForClient(principal.id(), id));
    }
}
