package com.qiao.contract.domain;

import java.util.Set;

public enum MilestoneStatus {
    PENDING_FUNDING,
    FUNDED,
    SUBMITTED,
    REVISION_REQUESTED,
    ACCEPTED,
    DISPUTED,
    REFUNDED;

    public static final Set<MilestoneStatus> FINAL = Set.of(ACCEPTED, REFUNDED);
}
