package com.qiao.admin.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.admin.domain.AdminRole;
import com.qiao.admin.domain.AdminStatus;
import com.qiao.admin.domain.AdminUser;
import com.qiao.admin.domain.AdminUserMapper;
import com.qiao.admin.web.AdminLoginResponse;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.ratelimit.RateLimiter;
import com.qiao.common.security.IssuedToken;
import com.qiao.common.security.JwtService;
import com.qiao.common.util.Texts;
import com.qiao.common.web.RequestMeta;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Admin password login with lockout after repeated failures and optional TOTP. Not transactional as a whole: each
 * attempt runs in its own short transaction so that failure counters persist even though the request fails.
 */
@Service
public class AdminAuthService {

    private final AdminUserService adminUserService;
    private final AdminUserMapper adminMapper;
    private final AdminProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final TotpVerifier totpVerifier;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final String dummyHash;

    public AdminAuthService(AdminUserService adminUserService, AdminUserMapper adminMapper, AdminProperties properties,
                            PasswordEncoder passwordEncoder, TotpVerifier totpVerifier, JwtService jwtService,
                            RateLimiter rateLimiter, AuditService auditService, Clock clock,
                            TransactionTemplate transactionTemplate) {
        this.adminUserService = adminUserService;
        this.adminMapper = adminMapper;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.totpVerifier = totpVerifier;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AdminLoginResponse login(String email, String password, String totp) {
        rateLimiter.check("admin-login:ip:" + RequestMeta.current().ip(), properties.loginAttemptsPerIpPerMinute(),
                Duration.ofMinutes(1));
        AdminUser found = adminUserService.findByEmail(email).orElse(null);
        if (found == null) {
            passwordEncoder.matches(password, dummyHash);
            auditService.log(Actor.system(), AuditAction.ADMIN_LOGIN_FAILED, "admin_user", null,
                    Map.of("email", Texts.normalizeEmail(email), "reason", "UNKNOWN_EMAIL"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        // The lock is checked and failures are counted while holding the admin row lock, so parallel guesses are
        // serialized and cannot exceed the limit. The outcome is committed before any error is thrown.
        Attempt attempt = transactionTemplate.execute(status -> attempt(found.getId(), password, totp));
        if (attempt.error() != null) {
            throw new ApiException(attempt.error());
        }
        AdminUser admin = attempt.admin();
        IssuedToken token = jwtService.issueAdminToken(admin.getId(),
                admin.roleSet().stream().map(AdminRole::name).toList());
        return new AdminLoginResponse(token.token(), new AdminLoginResponse.Admin(admin.getId(), admin.getEmail(),
                admin.roleSet()));
    }

    private Attempt attempt(long adminId, String password, String totp) {
        AdminUser admin = adminMapper.selectByIdForUpdate(adminId);
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            return Attempt.failed(ErrorCode.ACCOUNT_DISABLED);
        }
        Instant now = clock.instant();
        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(now)) {
            return Attempt.failed(ErrorCode.ADMIN_LOCKED);
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            registerFailure(admin, "BAD_PASSWORD", now);
            return Attempt.failed(ErrorCode.INVALID_CREDENTIALS);
        }
        if (admin.getTotpSecret() != null) {
            if (totp == null || totp.isBlank()) {
                return Attempt.failed(ErrorCode.TOTP_REQUIRED);
            }
            if (!totpVerifier.verify(admin.getTotpSecret(), totp.trim())) {
                registerFailure(admin, "BAD_TOTP", now);
                return Attempt.failed(ErrorCode.TOTP_INVALID);
            }
        }
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getFailedAttempts, 0)
                .set(AdminUser::getLockedUntil, null)
                .set(AdminUser::getUpdatedAt, now)
                .eq(AdminUser::getId, admin.getId()));
        auditService.log(Actor.admin(admin.getId()), AuditAction.ADMIN_LOGIN, "admin_user", admin.getId());
        return new Attempt(admin, null);
    }

    /**
     * Counts a failure on the locked row; the fifth one locks the account and resets the counter.
     */
    private void registerFailure(AdminUser admin, String reason, Instant now) {
        int failures = (admin.getFailedAttempts() == null ? 0 : admin.getFailedAttempts()) + 1;
        boolean locked = failures >= properties.maxFailedAttempts();
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getFailedAttempts, locked ? 0 : failures)
                .set(locked, AdminUser::getLockedUntil, now.plus(properties.lockDuration()))
                .set(AdminUser::getUpdatedAt, now)
                .eq(AdminUser::getId, admin.getId()));
        auditService.log(Actor.admin(admin.getId()), AuditAction.ADMIN_LOGIN_FAILED, "admin_user", admin.getId(),
                Map.of("reason", reason, "locked", locked));
    }

    private record Attempt(AdminUser admin, ErrorCode error) {

        static Attempt failed(ErrorCode error) {
            return new Attempt(null, error);
        }
    }
}
