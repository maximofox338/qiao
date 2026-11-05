package com.qiao.contract.web;

import com.qiao.contract.domain.ContractDocument;
import com.qiao.contract.domain.DocumentKind;

import java.time.Instant;

public record ContractDocumentView(Long id, DocumentKind kind, String templateVersion, String locale, String body,
                                   String sha256, boolean accepted, Long acceptedByUserId, Instant acceptedAt) {

    public static ContractDocumentView from(ContractDocument d) {
        return new ContractDocumentView(d.getId(), d.getKind(), d.getTemplateVersion(), d.getLocale(), d.getBody(),
                d.getSha256(), d.getAcceptedAt() != null, d.getAcceptedByUserId(), d.getAcceptedAt());
    }
}
