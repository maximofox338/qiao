package com.qiao.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.account.domain.AppUser;
import com.qiao.account.domain.AppUserMapper;
import com.qiao.account.domain.UserRole;
import com.qiao.account.domain.UserStatus;
import com.qiao.common.error.ApiException;
import com.qiao.common.util.Texts;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final AppUserMapper userMapper;
    private final Clock clock;

    public UserService(AppUserMapper userMapper, Clock clock) {
        this.userMapper = userMapper;
        this.clock = clock;
    }

    public Optional<AppUser> findByEmail(String email) {
        return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, Texts.normalizeEmail(email))));
    }

    public AppUser get(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.notFound("User");
        }
        return user;
    }

    public boolean isActive(long userId) {
        return userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .eq(AppUser::getStatus, UserStatus.ACTIVE)) > 0;
    }

    public AppUser create(String email, UserRole role, String locale) {
        AppUser user = new AppUser();
        user.setEmail(Texts.normalizeEmail(email));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setLocale(locale);
        userMapper.insert(user);
        return user;
    }

    public void updateLocale(long userId, String locale) {
        userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .set(AppUser::getLocale, locale)
                .set(AppUser::getUpdatedAt, clock.instant())
                .eq(AppUser::getId, userId));
    }

    public void recordLogin(long userId) {
        Instant now = clock.instant();
        userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .set(AppUser::getLastLoginAt, now)
                .set(AppUser::getOtpLockedUntil, null)
                .set(AppUser::getUpdatedAt, now)
                .eq(AppUser::getId, userId));
    }

    public void lockOtp(long userId, Instant until) {
        userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .set(AppUser::getOtpLockedUntil, until)
                .set(AppUser::getUpdatedAt, clock.instant())
                .eq(AppUser::getId, userId));
    }

    /**
     * Frees the email address and marks the account deleted; the row is kept for transactional history.
     */
    public void anonymize(long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .set(AppUser::getEmail, "deleted-" + userId + "@deleted.invalid")
                .set(AppUser::getStatus, UserStatus.DELETED)
                .set(AppUser::getOtpLockedUntil, null)
                .set(AppUser::getUpdatedAt, clock.instant())
                .eq(AppUser::getId, userId));
    }

    public Map<Long, String> emails(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));
    }
}
