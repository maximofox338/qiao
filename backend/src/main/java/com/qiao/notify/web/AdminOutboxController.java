package com.qiao.notify.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.notify.service.MailService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('OPS', 'COMPLIANCE')")
public class AdminOutboxController {

    private final MailService mailService;

    public AdminOutboxController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping("/api/admin/outbox")
    public ApiResponse<PageResult<MailOutboxView>> list(@RequestParam(required = false) String to, PageQuery pageQuery) {
        return ApiResponse.ok(mailService.search(to, pageQuery));
    }
}
