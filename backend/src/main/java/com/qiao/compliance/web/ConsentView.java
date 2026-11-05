package com.qiao.compliance.web;

import com.qiao.compliance.domain.Consent;
import com.qiao.compliance.domain.ConsentType;

import java.time.Instant;

public record ConsentView(ConsentType type, String version, boolean granted, String scopeRef, Instant createdAt) {

    public static ConsentView from(Consent consent) {
        return new ConsentView(consent.getType(), consent.getVersion(), Boolean.TRUE.equals(consent.getGranted()),
                consent.getScopeRef(), consent.getCreatedAt());
    }
}
