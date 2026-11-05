package com.qiao.admin.service;

import com.qiao.admin.domain.AdminRole;
import com.qiao.admin.domain.AdminStatus;
import com.qiao.admin.domain.AdminUser;
import com.qiao.admin.domain.AdminUserMapper;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The invariants of {@link AdminUserService#disable}, checked on the rows read under lock. Through the API the caller
 * is always an active SUPERADMIN (its token says so), so "last active SUPERADMIN" only happens when the database moved
 * on since the token was issued (roles edited by hand, a concurrent disable): exactly what these cases simulate.
 */
class AdminLifecycleGuardTest {

    @BeforeAll
    static void tableMetadata() {
        // lambda update wrappers need the entity's table metadata, which Spring Boot builds outside unit tests
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AdminUser.class);
    }

    private final AdminUserMapper mapper = mock(AdminUserMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final AdminUserService service = new AdminUserService(mapper, new BCryptPasswordEncoder(4), audit,
            Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void nobodyDisablesThemselves() {
        assertThatThrownBy(() -> service.disable(1, 1)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.CANNOT_DISABLE_SELF);
        verifyNoInteractions(mapper, audit);
    }

    @Test
    void theLastActiveSuperadminCannotBeDisabled() {
        // the caller's token still claims SUPERADMIN, but its roles were reduced to OPS in the database
        when(mapper.selectList(any())).thenReturn(List.of(admin(1, AdminStatus.ACTIVE, AdminRole.OPS),
                admin(2, AdminStatus.ACTIVE, AdminRole.SUPERADMIN), admin(3, AdminStatus.DISABLED, AdminRole.SUPERADMIN)));
        assertThatThrownBy(() -> service.disable(1, 2)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.LAST_SUPERADMIN);
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void aCallerDisabledMeanwhileCannotDisableOthers() {
        when(mapper.selectList(any())).thenReturn(List.of(admin(1, AdminStatus.DISABLED, AdminRole.SUPERADMIN),
                admin(2, AdminStatus.ACTIVE, AdminRole.SUPERADMIN)));
        assertThatThrownBy(() -> service.disable(1, 2)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void aSuperadminCanBeDisabledWhileAnotherOneStaysActive() {
        AdminUser target = admin(2, AdminStatus.ACTIVE, AdminRole.SUPERADMIN);
        when(mapper.selectList(any())).thenReturn(List.of(admin(1, AdminStatus.ACTIVE, AdminRole.SUPERADMIN), target));
        when(mapper.selectById(2L)).thenReturn(target);
        assertThat(service.disable(1, 2).id()).isEqualTo(2L);
        verify(mapper).update(any(), any());
        verify(audit).log(any(), eq(AuditAction.ADMIN_DISABLED), eq("admin_user"), eq(2L), anyMap());
    }

    private static AdminUser admin(long id, AdminStatus status, AdminRole role) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setEmail("admin" + id + "@test.qiao");
        admin.setStatus(status);
        admin.setRoleSet(Set.of(role));
        admin.setTokenVersion(0);
        return admin;
    }
}
