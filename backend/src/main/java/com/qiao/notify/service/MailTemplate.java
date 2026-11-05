package com.qiao.notify.service;

/**
 * Localized mail templates; texts live in {@code resources/i18n/mail*.properties} as
 * {@code mail.<key>.subject} / {@code mail.<key>.body}.
 */
public enum MailTemplate {
    OTP("otp", true),
    COMPANY_APPROVED("company.approved", false),
    COMPANY_REJECTED("company.rejected", false),
    ENGINEER_APPROVED("engineer.approved", false),
    ENGINEER_REJECTED("engineer.rejected", false),
    PROJECT_APPROVED("project.approved", false),
    PROJECT_REJECTED("project.rejected", false),
    PROJECT_CHANGES_REQUESTED("project.changes", false),
    APPLICATION_HIRED("application.hired", false),
    MILESTONE_FUNDED("milestone.funded", false),
    MILESTONE_SUBMITTED("milestone.submitted", false),
    MILESTONE_ACCEPTED("milestone.accepted", false),
    MILESTONE_REVISION_REQUESTED("milestone.revision", false),
    TEAM_INVITATION("team.invitation", false);

    private final String key;
    private final boolean sensitive;

    MailTemplate(String key, boolean sensitive) {
        this.key = key;
        this.sensitive = sensitive;
    }

    public String key() {
        return key;
    }

    public boolean sensitive() {
        return sensitive;
    }
}
