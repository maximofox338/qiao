package com.qiao.delivery.web;

import com.qiao.delivery.domain.Attachment;

public record AttachmentView(Long id, Long milestoneId, String filename, String contentType, long sizeBytes,
                             String sha256) {

    public static AttachmentView from(Attachment a) {
        return new AttachmentView(a.getId(), a.getMilestoneId(), a.getFilename(), a.getContentType(), a.getSizeBytes(),
                a.getSha256());
    }
}
