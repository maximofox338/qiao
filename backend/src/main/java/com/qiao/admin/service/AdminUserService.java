package com.qiao.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.admin.domain.AdminRole;
import com.qiao.admin.domain.AdminStatus;
import com.qiao.admin.domain.AdminUser;
import com.qiao.admin.domain.AdminUserMapper;
import com.qiao.admin.web.AdminView;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.util.Texts;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Admin accounts: creation, listing and the SUPERADMIN lifecycle (disable / enable). Disabling increments the admin's
 * token version, so its sessions end at once and are not revived by a later enable.
 */
@Service
public class AdminUserService {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminUserMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public AdminUserService(AdminUserMapper adminMapper, PasswordEncoder passwordEncoder, AuditService auditService,
                            Clock clock) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    public AdminView create(String email, String password, Set<AdminRole> roles, Actor createdBy) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Admin passwords need at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        AdminUser admin = new AdminUser();
        admin.setEmail(Texts.normalizeEmail(email));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRoleSet(roles);
        admin.setFailedAttempts(0);
        admin.setTokenVersion(0);
        admin.setStatus(AdminStatus.ACTIVE);
        try {
            adminMapper.insert(admin);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        auditService.log(createdBy, AuditAction.ADMIN_CREATED, "admin_user", admin.getId(),
                Map.of("email", admin.getEmail(), "roles", admin.getRoles()));
        return AdminView.from(admin);
    }

    public List<AdminView> list() {
        return adminMapper.selectList(new LambdaQueryWrapper<AdminUser>().orderByAsc(AdminUser::getId))
                .stream().map(AdminView::from).toList();
    }

    public AdminUser get(long adminId) {
        AdminUser admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw ApiException.notFound("Admin");
        }
        return admin;
    }

    public Optional<AdminUser> findByEmail(String email) {
        return Optional.ofNullable(adminMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getEmail, Texts.normalizeEmail(email))));
    }

    /**
     * Whether a token of this admin, issued with {@code tokenVersion}, is still valid: the admin is ACTIVE and the
     * version was not incremented since (password change, disable). One indexed query per request.
     */
    public boolean acceptsToken(long adminId, int tokenVersion) {
        return adminMapper.exists(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getId, adminId)
                .eq(AdminUser::getStatus, AdminStatus.ACTIVE)
                .eq(AdminUser::getTokenVersion, tokenVersion));
    }

    /**
     * SUPERADMIN only (checked by the controller). Refused for oneself ({@code CANNOT_DISABLE_SELF}) and for the last
     * active SUPERADMIN ({@code LAST_SUPERADMIN}); disabling an already disabled admin changes nothing. Every admin row
     * is locked first, so two SUPERADMINs disabling each other at the same time are serialized and cannot leave the
     * back-office without an active SUPERADMIN.
     */
    @Transactional
    public AdminView disable(long callerId, long targetId) {
        if (callerId == targetId) {
            throw new ApiException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        List<AdminUser> admins = lockAll();
        AdminUser target = find(admins, targetId);
        requireActiveCaller(admins, callerId);
        if (target.getStatus() == AdminStatus.DISABLED) {
            return AdminView.from(target);
        }
        boolean otherActiveSuperadmin = admins.stream().anyMatch(a -> a.getId() != targetId
                && a.getStatus() == AdminStatus.ACTIVE && a.roleSet().contains(AdminRole.SUPERADMIN));
        if (target.roleSet().contains(AdminRole.SUPERADMIN) && !otherActiveSuperadmin) {
            throw new ApiException(ErrorCode.LAST_SUPERADMIN);
        }
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getStatus, AdminStatus.DISABLED)
                .set(AdminUser::getTokenVersion, target.tokenVersionOrZero() + 1)
                .set(AdminUser::getUpdatedAt, clock.instant())
                .eq(AdminUser::getId, targetId));
        auditService.log(Actor.admin(callerId), AuditAction.ADMIN_DISABLED, "admin_user", targetId,
                Map.of("email", target.getEmail(), "roles", target.getRoles(), "sessionsRevoked", true));
        return AdminView.from(adminMapper.selectById(targetId));
    }

    /**
     * SUPERADMIN only. Re-activates a disabled admin and clears a pending login lock; sessions from before the disable
     * stay invalid (their token version is older), so the admin signs in again.
     */
    @Transactional
    public AdminView enable(long callerId, long targetId) {
        List<AdminUser> admins = lockAll();
        AdminUser target = find(admins, targetId);
        requireActiveCaller(admins, callerId);
        if (target.getStatus() == AdminStatus.ACTIVE) {
            return AdminView.from(target);
        }
        adminMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .set(AdminUser::getStatus, AdminStatus.ACTIVE)
                .set(AdminUser::getFailedAttempts, 0)
                .set(AdminUser::getLockedUntil, null)
                .set(AdminUser::getUpdatedAt, clock.instant())
                .eq(AdminUser::getId, targetId));
        auditService.log(Actor.admin(callerId), AuditAction.ADMIN_ENABLED, "admin_user", targetId,
                Map.of("email", target.getEmail(), "roles", target.getRoles()));
        return AdminView.from(adminMapper.selectById(targetId));
    }

    /**
     * Every admin row, locked in id order (the table is small; this serializes lifecycle changes).
     */
    private List<AdminUser> lockAll() {
        return adminMapper.selectList(new QueryWrapper<AdminUser>().orderByAsc("id").last("FOR UPDATE"));
    }

    private static AdminUser find(List<AdminUser> admins, long adminId) {
        return admins.stream().filter(a -> a.getId() == adminId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Admin"));
    }

    /**
     * The caller's token was checked when the request started; a concurrent disable may have committed since.
     */
    private static void requireActiveCaller(List<AdminUser> admins, long callerId) {
        boolean active = admins.stream().anyMatch(a -> a.getId() == callerId && a.getStatus() == AdminStatus.ACTIVE);
        if (!active) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    public boolean noneExists() {
        return adminMapper.selectCount(null) == 0;
    }

    public Optional<AdminUser> firstSuperadmin() {
        return adminMapper.selectList(new LambdaQueryWrapper<AdminUser>().orderByAsc(AdminUser::getId)).stream()
                .filter(a -> a.roleSet().contains(AdminRole.SUPERADMIN))
                .findFirst();
    }
}
