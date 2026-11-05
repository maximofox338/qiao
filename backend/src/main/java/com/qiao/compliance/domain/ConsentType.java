package com.qiao.compliance.domain;

/**
 * Consent types (spec §1.8). {@code document} is the legal text whose version is recorded with the consent.
 */
public enum ConsentType {
    TERMS("terms", false),
    PRIVACY("privacy", false),
    CROSS_BORDER_PROCESSING("cross-border", false),
    SHARE_PROFILE_PSEUDONYMIZED("privacy", false),
    SHARE_FULL_CV("privacy", true);

    private final String document;
    private final boolean scoped;

    ConsentType(String document, boolean scoped) {
        this.document = document;
        this.scoped = scoped;
    }

    public String document() {
        return document;
    }

    /**
     * Scoped consents apply to one {@code scopeRef} (a project id for {@link #SHARE_FULL_CV}).
     */
    public boolean scoped() {
        return scoped;
    }
}
