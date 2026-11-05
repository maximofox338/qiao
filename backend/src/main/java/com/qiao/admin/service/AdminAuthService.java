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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Admin password login with lockout after repeated failures and optional TOTP. Not transactional: failure counters
 * must persist even though the request fails.
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
    private final String dummyHash;

    public AdminAuthService(AdminUserService adminUserService, AdminUserMapper adminMapper, AdminProperties properties,
                            PasswordEncoder passwordEncoder, TotpVerifier totpVerifier, JwtService jwtService,
                            RateLimiter rateLimiter, AuditService auditService, Clock clock) {
        this.adminUserService = adminUserService;
        this.adminMapper = adminMapper;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.totpVerifier = totpVerifier;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AdminLoginResponse login(String email, String password, String totp) {
        rateLimiter.check("admin-login:ip:" + RequestMeta.current().ip(), properties.loginAttemptsPerIpPerMinute(),
                Duration.ofMinutes(1));
        AdminUser admin = adminUserService.findByEmail(email).orElse(null);
        if (admin == null) {
            passwordEncoder.matches(password, dummyHash);
            auditService.log(Actor.system(), AuditAction.ADMIN_LOGIN_FAILED, "admin_user", null,
                    Map.of("email", Texts.normalizeEmail(email), "reason", "UNKNOWN_EMAIL"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        Instant now = clock.instant();
        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.ADMIN_LOCKED);
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            registerFailure(admin, "BAD_PASSWORD");
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (admin.getTotpSecret() != null) {
            if (totp == null || totp.isBlank()) {
                throw new ApiException(ErrorCode.TOTP_REQUIRED);
            }
            if (!totpVerifier.verify(admin.getTotpSecret(), totp.trim())) {
                registerFailure(admin, "BAD_TOTP");
                throw new ApiException(ErrorCode.TOTP_INVALID);
            }
        }
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getFailedAttempts, 0)
                .set(AdminUser::getLockedUntil, null)
                .set(AdminUser::getUpdatedAt, now)
                .eq(AdminUser::getId, admin.getId()));
        auditService.log(Actor.admin(admin.getId()), AuditAction.ADMIN_LOGIN, "admin_user", admin.getId());
        IssuedToken token = jwtService.issueAdminToken(admin.getId(),
                admin.roleSet().stream().map(AdminRole::name).toList());
        return new AdminLoginResponse(token.token(), new AdminLoginResponse.Admin(admin.getId(), admin.getEmail(),
                admin.roleSet()));
    }

    private void registerFailure(AdminUser admin, String reason) {
        Instant now = clock.instant();
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .setSql("failed_attempts = failed_attempts + 1")
                .set(AdminUser::getUpdatedAt, now)
                .eq(AdminUser::getId, admin.getId()));
        AdminUser current = adminUserService.get(admin.getId());
        boolean locked = current.getFailedAttempts() >= properties.maxFailedAttempts();
        if (locked) {
            adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                    .set(AdminUser::getFailedAttempts, 0)
                    .set(AdminUser::getLockedUntil, now.plus(properties.lockDuration()))
                    .set(AdminUser::getUpdatedAt, now)
                    .eq(AdminUser::getId, admin.getId()));
        }
        auditService.log(Actor.admin(admin.getId()), AuditAction.ADMIN_LOGIN_FAILED, "admin_user", admin.getId(),
                Map.of("reason", reason, "locked", locked));
    }
}
