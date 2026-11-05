package com.qiao.delivery.service;

import com.qiao.account.service.PersonalDataHandler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Messages and files are transactional records of a contract: exported, and kept on deletion (3-year retention).
 */
@Component
public class DeliveryPersonalDataHandler implements PersonalDataHandler {

    private final MessageService messageService;
    private final AttachmentService attachmentService;

    public DeliveryPersonalDataHandler(MessageService messageService, AttachmentService attachmentService) {
        this.messageService = messageService;
        this.attachmentService = attachmentService;
    }

    @Override
    public String section() {
        return "delivery";
    }

    @Override
    public Object export(long userId) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("messages", messageService.sentBy(userId).stream().map(m -> Map.of(
                "id", m.getId(), "contractId", m.getContractId(), "body", m.getBody(), "createdAt", m.getCreatedAt())).toList());
        export.put("attachments", attachmentService.uploadedBy(userId).stream().map(a -> Map.of(
                "id", a.getId(), "contractId", a.getContractId(), "filename", a.getFilename(),
                "sizeBytes", a.getSizeBytes(), "sha256", a.getSha256(), "createdAt", a.getCreatedAt())).toList());
        return export;
    }
}
