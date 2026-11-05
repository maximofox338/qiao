package com.qiao.contract.service;

import com.qiao.account.service.PersonalDataHandler;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deletion is refused while a contract is ACTIVE; contracts still pending signatures are terminated.
 */
@Component
public class ContractPersonalDataHandler implements PersonalDataHandler {

    private final ContractService contractService;

    public ContractPersonalDataHandler(ContractService contractService) {
        this.contractService = contractService;
    }

    @Override
    public String section() {
        return "contracts";
    }

    @Override
    public Object export(long userId) {
        return contractService.contractsOf(userId).stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("projectId", c.getProjectId());
            row.put("status", c.getStatus());
            row.put("activatedAt", c.getActivatedAt());
            row.put("completedAt", c.getCompletedAt());
            return row;
        }).toList();
    }

    @Override
    public void assertDeletable(long userId) {
        boolean active = contractService.contractsOf(userId).stream().map(Contract::getStatus)
                .anyMatch(status -> status == ContractStatus.ACTIVE);
        if (active) {
            throw new ApiException(ErrorCode.ACCOUNT_HAS_ACTIVE_CONTRACTS);
        }
    }

    @Override
    public void anonymize(long userId) {
        contractService.terminatePending(userId);
    }
}
