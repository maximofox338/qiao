package com.qiao.project.domain;

import java.util.Set;

public enum ApplicationStatus {
    SUBMITTED,
    SHORTLISTED,
    SELECTED,
    HIRED,
    DECLINED,
    WITHDRAWN;

    /**
     * Statuses from which an application can still be declined or withdrawn.
     */
    public static final Set<ApplicationStatus> ACTIVE = Set.of(SUBMITTED, SHORTLISTED, SELECTED);
}
