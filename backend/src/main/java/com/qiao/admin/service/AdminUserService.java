package com.qiao.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AdminUserService {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminUserMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminUserService(AdminUserMapper adminMapper, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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

    public boolean isActive(long adminId) {
        return adminMapper.exists(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getId, adminId)
                .eq(AdminUser::getStatus, AdminStatus.ACTIVE));
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
