package com.qiao.billing.web;

import com.qiao.billing.domain.SettlementBatchStatus;
import com.qiao.billing.service.SettlementService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/settlements")
@PreAuthorize("hasRole('FINANCE')")
public class AdminSettlementController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    public ApiResponse<SettlementBatchView> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @Valid @RequestBody CreateSettlementRequest request) {
        return ApiResponse.ok(settlementService.create(request.period(), principal.id()));
    }

    @GetMapping
    public ApiResponse<List<SettlementBatchView>> list(@RequestParam(required = false) SettlementBatchStatus status) {
        return ApiResponse.ok(settlementService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SettlementBatchView> get(@PathVariable long id) {
        return ApiResponse.ok(settlementService.get(id));
    }

    @GetMapping("/{id}/export.csv")
    public ResponseEntity<String> exportCsv(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        String csv = settlementService.exportCsv(id, principal.id());
        ContentDisposition disposition = ContentDisposition.attachment().filename("settlement-" + id + ".csv").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(TEXT_CSV)
                .body(csv);
    }

    @PostMapping("/{id}/mark-exported")
    public ApiResponse<SettlementBatchView> markExported(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable long id) {
        return ApiResponse.ok(settlementService.markExported(id, principal.id()));
    }
}
