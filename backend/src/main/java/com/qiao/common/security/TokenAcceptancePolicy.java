package com.qiao.common.security;

/**
 * Extra checks on a cryptographically valid token (denylist, account status). Implemented by the owning modules.
 */
public interface TokenAcceptancePolicy {

    boolean accepts(AuthPrincipal principal);
}
