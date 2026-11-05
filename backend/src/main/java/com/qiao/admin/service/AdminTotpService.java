package com.qiao.admin.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.admin.domain.AdminUser;
import com.qiao.admin.domain.AdminUserMapper;
import com.qiao.admin.web.AdminLoginResponse;
import com.qiao.admin.web.AdminView;
import com.qiao.admin.web.TotpSetupView;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.ratelimit.RateLimiter;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;

/**
 * TOTP enrolment for admins: setup (server-generated secret, pending) → enable with a valid code → disable with a
 * valid code; a SUPERADMIN can reset another admin's TOTP. Every accepted code records its time-step, and a code of
 * that step or an earlier one is refused afterwards (no replay within the drift window).
 */
@Service
public class AdminTotpService {

    public static final String ISSUER = "Qiao";
    private static final int CODE_ATTEMPTS_PER_MINUTE = 10;

    private final AdminUserMapper adminMapper;
    private final TotpVerifier totpVerifier;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final Clock clock;

    public AdminTotpService(AdminUserMapper adminMapper, TotpVerifier totpVerifier, RateLimiter rateLimiter,
                            AuditService auditService, Clock clock) {
        this.adminMapper = adminMapper;
        this.totpVerifier = totpVerifier;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Generates a new pending secret (replacing an unconfirmed one) and returns it with its otpauth URI.
     */
    @Transactional
    public TotpSetupView setup(long adminId) {
        AdminUser admin = lock(adminId);
        if (admin.getTotpSecret() != null) {
            throw new ApiException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        String secret = totpVerifier.generateSecret();
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getTotpPendingSecret, secret)
                .set(AdminUser::getUpdatedAt, clock.instant())
                .eq(AdminUser::getId, adminId));
        return new TotpSetupView(secret, TotpVerifier.otpauthUri(ISSUER, admin.getEmail(), secret), ISSUER, admin.getEmail());
    }

    @Transactional
    public AdminLoginResponse.Admin enable(long adminId, String code) {
        rateLimiter.check("admin-totp:" + adminId, CODE_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));
        AdminUser admin = lock(adminId);
        if (admin.getTotpSecret() != null) {
            throw new ApiException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        if (admin.getTotpPendingSecret() == null) {
            throw new ApiException(ErrorCode.TOTP_SETUP_REQUIRED);
        }
        long step = acceptedStep(admin.getTotpPendingSecret(), admin.getTotpLastStep(), code);
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getTotpSecret, admin.getTotpPendingSecret())
                .set(AdminUser::getTotpPendingSecret, null)
                .set(AdminUser::getTotpLastStep, step)
                .set(AdminUser::getUpdatedAt, clock.instant())
                .eq(AdminUser::getId, adminId));
        auditService.log(Actor.admin(adminId), AuditAction.ADMIN_TOTP_ENABLED, "admin_user", adminId);
        return AdminLoginResponse.Admin.from(adminMapper.selectById(adminId));
    }

    @Transactional
    public AdminLoginResponse.Admin disable(long adminId, String code) {
        rateLimiter.check("admin-totp:" + adminId, CODE_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));
        AdminUser admin = lock(adminId);
        if (admin.getTotpSecret() == null) {
            throw new ApiException(ErrorCode.TOTP_NOT_ENABLED);
        }
        acceptedStep(admin.getTotpSecret(), admin.getTotpLastStep(), code);
        clear(adminId);
        auditService.log(Actor.admin(adminId), AuditAction.ADMIN_TOTP_DISABLED, "admin_user", adminId);
        return AdminLoginResponse.Admin.from(adminMapper.selectById(adminId));
    }

    /**
     * SUPERADMIN only (checked by the controller): removes another admin's TOTP, e.g. after a lost phone.
     */
    @Transactional
    public AdminView reset(long superadminId, long targetAdminId) {
        AdminUser target = lock(targetAdminId);
        clear(targetAdminId);
        auditService.log(Actor.admin(superadminId), AuditAction.ADMIN_TOTP_RESET, "admin_user", targetAdminId,
                Map.of("email", target.getEmail(), "wasEnabled", target.getTotpSecret() != null));
        return AdminView.from(adminMapper.selectById(targetAdminId));
    }

    private long acceptedStep(String secret, Long lastStep, String code) {
        OptionalLong step = totpVerifier.matchStep(secret, code == null ? null : code.trim());
        if (step.isEmpty() || (lastStep != null && step.getAsLong() <= lastStep)) {
            throw new ApiException(ErrorCode.TOTP_CODE_INVALID);
        }
        return step.getAsLong();
    }

    private void clear(long adminId) {
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getTotpSecret, null)
                .set(AdminUser::getTotpPendingSecret, null)
                .set(AdminUser::getTotpLastStep, null)
                .set(AdminUser::getUpdatedAt, clock.instant())
                .eq(AdminUser::getId, adminId));
    }

    private AdminUser lock(long adminId) {
        AdminUser admin = adminMapper.selectByIdForUpdate(adminId);
        if (admin == null) {
            throw ApiException.notFound("Admin");
        }
        return admin;
    }
}
