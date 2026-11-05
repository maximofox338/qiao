package com.qiao.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.account.domain.JwtDenylistEntry;
import com.qiao.account.domain.JwtDenylistMapper;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.security.TokenAcceptancePolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Revoked token ids (logout, account deletion). Applies to user and admin tokens.
 */
@Service
public class TokenDenylistService implements TokenAcceptancePolicy {

    private final JwtDenylistMapper denylistMapper;
    private final Clock clock;

    public TokenDenylistService(JwtDenylistMapper denylistMapper, Clock clock) {
        this.denylistMapper = denylistMapper;
        this.clock = clock;
    }

    @Override
    public boolean accepts(AuthPrincipal principal) {
        return principal.tokenId() == null || !denylistMapper.exists(new LambdaQueryWrapper<JwtDenylistEntry>()
                .eq(JwtDenylistEntry::getJti, principal.tokenId()));
    }

    public void revoke(AuthPrincipal principal) {
        if (principal.tokenId() == null) {
            return;
        }
        JwtDenylistEntry entry = new JwtDenylistEntry();
        entry.setJti(principal.tokenId());
        entry.setExpiresAt(principal.tokenExpiresAt());
        try {
            denylistMapper.insert(entry);
        } catch (DuplicateKeyException alreadyRevoked) {
            // idempotent logout
        }
    }

    @Scheduled(cron = "0 23 * * * *")
    void purgeExpired() {
        denylistMapper.delete(new LambdaQueryWrapper<JwtDenylistEntry>()
                .lt(JwtDenylistEntry::getExpiresAt, clock.instant()));
    }
}
