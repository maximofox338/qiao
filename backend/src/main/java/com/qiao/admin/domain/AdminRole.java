package com.qiao.admin.domain;

/**
 * Back-office roles; an admin may hold several. SUPERADMIN implies all others (see SecurityConfig).
 */
public enum AdminRole {
    SUPERADMIN,
    OPS,
    FINANCE,
    COMPLIANCE
}
