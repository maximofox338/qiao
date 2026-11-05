package com.qiao.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.contract.service.AttachmentDirectory;
import com.qiao.contract.service.AttachmentSummary;
import com.qiao.delivery.domain.Attachment;
import com.qiao.delivery.domain.AttachmentMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Feeds contract views with attachment metadata. Depends only on the mapper to avoid a bean cycle with
 * {@link com.qiao.contract.service.ContractService}.
 */
@Component
public class DeliveryAttachmentDirectory implements AttachmentDirectory {

    private final AttachmentMapper attachmentMapper;

    public DeliveryAttachmentDirectory(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public List<AttachmentSummary> list(long contractId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getContractId, contractId)
                        .orderByAsc(Attachment::getId))
                .stream()
                .map(a -> new AttachmentSummary(a.getId(), a.getMilestoneId(), a.getUploaderUserId(), a.getFilename(),
                        a.getContentType(), a.getSizeBytes(), a.getCreatedAt()))
                .toList();
    }
}
