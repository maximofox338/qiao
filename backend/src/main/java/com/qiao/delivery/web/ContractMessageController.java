package com.qiao.delivery.web;

import com.qiao.common.api.ApiResponse;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.delivery.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contracts/{id}/messages")
public class ContractMessageController {

    private final MessageService messageService;

    public ContractMessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * The whole thread, oldest first. {@code page}/{@code size} query parameters are accepted but not applied:
     * chats are short and both apps accept a plain array.
     */
    @GetMapping
    public ApiResponse<List<MessageView>> list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        return ApiResponse.ok(messageService.list(principal.id(), id));
    }

    @PostMapping
    public ApiResponse<MessageView> post(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id,
                                         @Valid @RequestBody MessageRequest request) {
        return ApiResponse.ok(messageService.post(principal.id(), id, request.body()));
    }
}
