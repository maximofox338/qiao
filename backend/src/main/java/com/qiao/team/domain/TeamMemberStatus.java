package com.qiao.team.domain;

/**
 * INVITED → ACTIVE | DECLINED; ACTIVE → LEFT (member) | REMOVED (lead). DECLINED, LEFT and REMOVED can be invited again.
 */
public enum TeamMemberStatus {
    INVITED,
    ACTIVE,
    DECLINED,
    LEFT,
    REMOVED
}
