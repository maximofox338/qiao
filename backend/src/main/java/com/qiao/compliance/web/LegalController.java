package com.qiao.compliance.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.compliance.service.LegalDocument;
import com.qiao.compliance.service.LegalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegalController {

    private final LegalService legalService;

    public LegalController(LegalService legalService) {
        this.legalService = legalService;
    }

    @GetMapping("/api/public/legal/{doc}")
    public ApiResponse<LegalDocument> get(@PathVariable String doc, @RequestParam(required = false) String locale) {
        return ApiResponse.ok(legalService.get(doc, locale));
    }
}
