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
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Admin password login with lockout after repeated failures and optional TOTP (each code accepted once), and the
 * self-service password change. Not transactional as a whole: each attempt runs in its own short transaction so that
 * failure counters and audit entries persist even though the request fails.
 */
@Service
public class AdminAuthService {

    /**
     * Password-change attempts per admin (wrong current passwords included) in {@link #PASSWORD_CHANGE_WINDOW}.
     */
    static final int PASSWORD_CHANGE_ATTEMPTS = 5;
    static final Duration PASSWORD_CHANGE_WINDOW = Duration.ofMinutes(15);
    /**
     * Validation code of {@code newPassword} when it equals the current password.
     */
    public static final String SAME_AS_CURRENT = "SAME_AS_CURRENT";

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
            throw attempt.error();
        }
        return session(attempt.admin());
    }

    /**
     * Changes the caller's password after checking the current one, and ends every session of that admin (the token
     * version is incremented, so all its tokens, this one included, stop being accepted). The response carries a new
     * token so the caller stays signed in on this device. Wrong current passwords answer {@code CURRENT_PASSWORD_INVALID}
     * (400, not 401: the back-office signs out on any 401), are audited and are rate limited per admin; they do not
     * count towards the login lockout.
     */
    public AdminLoginResponse changePassword(long adminId, String currentPassword, String newPassword) {
        rateLimiter.check("admin-password:" + adminId, PASSWORD_CHANGE_ATTEMPTS, PASSWORD_CHANGE_WINDOW);
        if (newPassword == null || newPassword.length() < AdminUserService.MIN_PASSWORD_LENGTH) {
            throw ApiException.validation("newPassword", "Size");
        }
        Attempt attempt = transactionTemplate.execute(status -> passwordChange(adminId, currentPassword, newPassword));
        if (attempt.error() != null) {
            throw attempt.error();
        }
        return session(attempt.admin());
    }

    private Attempt passwordChange(long adminId, String currentPassword, String newPassword) {
        AdminUser admin = adminMapper.selectByIdForUpdate(adminId);
        if (admin == null || admin.getStatus() != AdminStatus.ACTIVE) {
            return Attempt.failed(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            auditService.log(Actor.admin(adminId), AuditAction.ADMIN_PASSWORD_CHANGE_FAILED, "admin_user", adminId,
                    Map.of("reason", "BAD_CURRENT_PASSWORD"));
            return Attempt.failed(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            return new Attempt(null, ApiException.validation("newPassword", SAME_AS_CURRENT));
        }
        int tokenVersion = admin.tokenVersionOrZero() + 1;
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getPasswordHash, passwordEncoder.encode(newPassword))
                .set(AdminUser::getTokenVersion, tokenVersion)
                .set(AdminUser::getFailedAttempts, 0)
                .set(AdminUser::getLockedUntil, null)
                .set(AdminUser::getUpdatedAt, clock.instant())
                .eq(AdminUser::getId, adminId));
        auditService.log(Actor.admin(adminId), AuditAction.ADMIN_PASSWORD_CHANGED, "admin_user", adminId,
                Map.of("sessionsRevoked", true));
        admin.setTokenVersion(tokenVersion);
        return new Attempt(admin, null);
    }

    private AdminLoginResponse session(AdminUser admin) {
        IssuedToken token = jwtService.issueAdminToken(admin.getId(),
                admin.roleSet().stream().map(AdminRole::name).toList(), admin.tokenVersionOrZero());
        return new AdminLoginResponse(token.token(), AdminLoginResponse.Admin.from(admin));
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
        Long totpStep = null;
        if (admin.getTotpSecret() != null) {
            if (totp == null || totp.isBlank()) {
                return Attempt.failed(ErrorCode.TOTP_REQUIRED);
            }
            // a code is valid once: its time-step must be later than the last accepted one (no replay)
            OptionalLong step = totpVerifier.matchStep(admin.getTotpSecret(), totp.trim());
            if (step.isEmpty() || (admin.getTotpLastStep() != null && step.getAsLong() <= admin.getTotpLastStep())) {
                registerFailure(admin, step.isEmpty() ? "BAD_TOTP" : "REPLAYED_TOTP", now);
                return Attempt.failed(ErrorCode.TOTP_INVALID);
            }
            totpStep = step.getAsLong();
        }
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getFailedAttempts, 0)
                .set(AdminUser::getLockedUntil, null)
                .set(totpStep != null, AdminUser::getTotpLastStep, totpStep)
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

    /**
     * Outcome of an attempt; the error is thrown only after the attempt's transaction committed.
     */
    private record Attempt(AdminUser admin, ApiException error) {

        static Attempt failed(ErrorCode error) {
            return new Attempt(null, new ApiException(error));
        }
    }
}
