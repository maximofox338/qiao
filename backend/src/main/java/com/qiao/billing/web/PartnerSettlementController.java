package com.qiao.billing.web;

import com.qiao.billing.domain.SettlementBatchStatus;
import com.qiao.billing.service.SettlementService;
import com.qiao.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Payroll partner API, authenticated with {@code X-Partner-Key}. Draft batches are never visible here.
 */
@RestController
@RequestMapping("/api/partner/settlements")
public class PartnerSettlementController {

    private final SettlementService settlementService;

    public PartnerSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public ApiResponse<List<SettlementBatchView>> list(@RequestParam(required = false) SettlementBatchStatus status) {
        return ApiResponse.ok(settlementService.partnerList(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SettlementBatchView> get(@PathVariable long id) {
        return ApiResponse.ok(settlementService.partnerGet(id));
    }

    @PostMapping("/{id}/lines/{lineId}/paid")
    public ApiResponse<SettlementLineView> linePaid(@PathVariable long id, @PathVariable long lineId,
                                                    @Valid @RequestBody LinePaidRequest request) {
        return ApiResponse.ok(settlementService.markLinePaid(id, lineId, request));
    }
}
