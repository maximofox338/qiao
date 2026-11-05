package com.qiao.compliance.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.compliance.domain.ActorType;
import com.qiao.compliance.service.AuditService;
import com.qiao.compliance.service.TransferService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('COMPLIANCE')")
public class AdminComplianceController {

    private final AuditService auditService;
    private final TransferService transferService;

    public AdminComplianceController(AuditService auditService, TransferService transferService) {
        this.auditService = auditService;
        this.transferService = transferService;
    }

    @GetMapping("/audit")
    public ApiResponse<PageResult<AuditLogView>> audit(@RequestParam(required = false) String action,
                                                       @RequestParam(required = false) ActorType actorType,
                                                       PageQuery pageQuery) {
        return ApiResponse.ok(auditService.search(action, actorType, pageQuery));
    }

    @GetMapping("/transfers/summary")
    public ApiResponse<TransferSummary> transfers(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(transferService.summary(year));
    }
}
