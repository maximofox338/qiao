package com.qiao.account.web;

import com.qiao.account.service.AccountService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.compliance.service.ConsentService;
import com.qiao.compliance.web.ConsentView;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccountService accountService;
    private final ConsentService consentService;

    public MeController(AccountService accountService, ConsentService consentService) {
        this.accountService = accountService;
        this.consentService = consentService;
    }

    @GetMapping
    public ApiResponse<MeView> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(accountService.me(principal.id()));
    }

    @PutMapping
    public ApiResponse<MeView> update(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody UpdateMeRequest request) {
        return ApiResponse.ok(accountService.updateLocale(principal.id(), request.locale()));
    }

    @GetMapping("/consents")
    public ApiResponse<List<ConsentView>> consents(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(consentService.latest(principal.id()));
    }

    @PostMapping("/consents")
    public ApiResponse<ConsentView> recordConsent(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody ConsentRequest request) {
        return ApiResponse.ok(consentService.record(principal.id(), request.type(), request.granted(),
                request.scopeRef(), null));
    }

    @GetMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> export(@AuthenticationPrincipal AuthPrincipal principal) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("qiao-export-" + principal.id() + ".json").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(ApiResponse.ok(accountService.export(principal.id())));
    }

    @PostMapping("/delete")
    public ApiResponse<Map<String, Boolean>> delete(@AuthenticationPrincipal AuthPrincipal principal) {
        accountService.delete(principal);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
