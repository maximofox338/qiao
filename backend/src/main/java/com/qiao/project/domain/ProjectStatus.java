package com.qiao.project.domain;

import java.util.Set;

public enum ProjectStatus {
    DRAFT,
    SUBMITTED,
    CHANGES_REQUESTED,
    REJECTED,
    OPEN,
    IN_CONTRACT,
    COMPLETED,
    CANCELLED;

    public static final Set<ProjectStatus> EDITABLE = Set.of(DRAFT, CHANGES_REQUESTED);
    public static final Set<ProjectStatus> CANCELLABLE = Set.of(DRAFT, SUBMITTED, CHANGES_REQUESTED, OPEN);
}
