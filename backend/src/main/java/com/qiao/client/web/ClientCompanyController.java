package com.qiao.client.web;

import com.qiao.client.service.CompanyService;
import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/company")
public class ClientCompanyController {

    private final CompanyService companyService;

    public ClientCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ApiResponse<CompanyView> get(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(companyService.getOwn(principal.id()));
    }

    @PutMapping
    public ApiResponse<CompanyView> update(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody CompanyRequest request) {
        return ApiResponse.ok(companyService.update(principal.id(), request));
    }

    @PostMapping("/declarations")
    public ApiResponse<CompanyView> declarations(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody DeclarationsRequest request) {
        return ApiResponse.ok(companyService.acceptDeclarations(principal.id(), request));
    }
}
