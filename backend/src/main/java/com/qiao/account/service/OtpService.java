package com.qiao.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiao.account.domain.AppUser;
import com.qiao.account.domain.OtpCode;
import com.qiao.account.domain.OtpCodeMapper;
import com.qiao.account.domain.UserStatus;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.ratelimit.RateLimiter;
import com.qiao.common.security.JwtProperties;
import com.qiao.common.util.Hashing;
import com.qiao.common.util.Locales;
import com.qiao.common.web.RequestMeta;
import com.qiao.notify.service.MailTemplate;
import com.qiao.notify.service.Notifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues and checks passwordless login codes. Callers are not transactional on purpose: failed attempts are committed
 * in their own short transaction and must persist even when the caller's operation fails.
 * <p>
 * The five-attempt budget belongs to the email, not to a single code: a new code inherits the failed attempts of the
 * code it replaces (while that code is younger than the lock duration), and attempts are counted under a row lock so
 * that parallel guesses cannot exceed the limit. Only the most recent code of an email can ever be used.
 */
@Service
public class OtpService {

    private final SecureRandom random = new SecureRandom();
    private final OtpCodeMapper otpCodeMapper;
    private final UserService userService;
    private final OtpProperties properties;
    private final RateLimiter rateLimiter;
    private final Notifier notifier;
    private final String hashKey;
    private final Clock clock;
    private final DevOtpRegistry devOtpRegistry;
    private final TransactionTemplate transactionTemplate;

    public OtpService(OtpCodeMapper otpCodeMapper, UserService userService, OtpProperties properties,
                      RateLimiter rateLimiter, Notifier notifier, JwtProperties jwtProperties, Clock clock,
                      ObjectProvider<DevOtpRegistry> devOtpRegistry, TransactionTemplate transactionTemplate) {
        this.otpCodeMapper = otpCodeMapper;
        this.userService = userService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.notifier = notifier;
        this.hashKey = jwtProperties.secret();
        this.clock = clock;
        this.devOtpRegistry = devOtpRegistry.getIfAvailable();
        this.transactionTemplate = transactionTemplate;
    }

    public void request(String email, String locale) {
        String ip = RequestMeta.current().ip();
        rateLimiter.check("otp:email:" + email, properties.requestsPerEmailPerMinute(), Duration.ofMinutes(1));
        rateLimiter.check("otp:ip:" + ip, properties.requestsPerIpPerHour(), Duration.ofHours(1));
        AppUser user = userService.findByEmail(email).orElse(null);
        ensureNotLocked(email, user);
        if (user != null && user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        Instant now = clock.instant();
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpCode otp = new OtpCode();
        otp.setEmail(email);
        otp.setCodeHash(hash(email, code));
        otp.setPurpose(OtpCode.PURPOSE_LOGIN);
        otp.setExpiresAt(now.plus(properties.ttl()));
        otp.setAttempts(inheritedAttempts(otpCodeMapper.selectOne(latestCode(email).last("LIMIT 1")), now));
        otp.setRequestIp(ip);
        otpCodeMapper.insert(otp);
        if (devOtpRegistry != null) {
            devOtpRegistry.remember(email, code);
        }
        String mailLocale = user != null ? user.getLocale() : Locales.normalize(locale, Locales.EN);
        notifier.send(email, mailLocale, MailTemplate.OTP, code, properties.ttl().toMinutes());
    }

    /**
     * Validates {@code code} against the email's most recent code without consuming it. Wrong codes count as attempts.
     */
    public OtpCode check(String email, String code) {
        AppUser user = userService.findByEmail(email).orElse(null);
        ensureNotLocked(email, user);
        String candidate = hash(email, code);
        CheckResult result = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            // the row lock serializes concurrent guesses on the same code
            OtpCode latest = otpCodeMapper.selectOne(latestCode(email).last("LIMIT 1 FOR UPDATE"));
            if (latest == null || latest.getUsedAt() != null || latest.getAttempts() >= properties.maxAttempts()) {
                return CheckResult.failed(ErrorCode.OTP_INVALID);
            }
            if (!latest.getExpiresAt().isAfter(now)) {
                return CheckResult.failed(ErrorCode.OTP_EXPIRED);
            }
            if (Hashing.constantTimeEquals(latest.getCodeHash(), candidate)) {
                return new CheckResult(latest, null);
            }
            otpCodeMapper.update(null, new LambdaUpdateWrapper<OtpCode>()
                    .setSql("attempts = attempts + 1")
                    .set(OtpCode::getUpdatedAt, now)
                    .eq(OtpCode::getId, latest.getId()));
            if (latest.getAttempts() + 1 >= properties.maxAttempts()) {
                if (user != null) {
                    userService.lockOtp(user.getId(), now.plus(properties.lockDuration()));
                }
                return CheckResult.failed(ErrorCode.OTP_LOCKED);
            }
            return CheckResult.failed(ErrorCode.OTP_INVALID);
        });
        if (result.error() != null) {
            throw new ApiException(result.error());
        }
        return result.otp();
    }

    public void consume(OtpCode otp) {
        int updated = otpCodeMapper.update(null, new LambdaUpdateWrapper<OtpCode>()
                .set(OtpCode::getUsedAt, clock.instant())
                .eq(OtpCode::getId, otp.getId())
                .isNull(OtpCode::getUsedAt));
        if (updated == 0) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
    }

    @Scheduled(cron = "0 17 * * * *")
    void purgeOldCodes() {
        otpCodeMapper.delete(new LambdaQueryWrapper<OtpCode>()
                .lt(OtpCode::getCreatedAt, clock.instant().minus(Duration.ofDays(1))));
    }

    /**
     * Registered users carry the lock on {@code app_user}; unknown emails are locked by an exhausted code.
     */
    private void ensureNotLocked(String email, AppUser user) {
        Instant lockedUntil;
        if (user != null) {
            lockedUntil = user.getOtpLockedUntil();
        } else {
            OtpCode exhausted = otpCodeMapper.selectOne(new LambdaQueryWrapper<OtpCode>()
                    .eq(OtpCode::getEmail, email)
                    .ge(OtpCode::getAttempts, properties.maxAttempts())
                    .orderByDesc(OtpCode::getId)
                    .last("LIMIT 1"));
            lockedUntil = exhausted == null ? null : exhausted.getUpdatedAt().plus(properties.lockDuration());
        }
        if (lockedUntil != null && lockedUntil.isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.OTP_LOCKED);
        }
    }

    private String hash(String email, String code) {
        return Hashing.hmacSha256Hex(hashKey, email + ":" + code);
    }

    private static LambdaQueryWrapper<OtpCode> latestCode(String email) {
        return new LambdaQueryWrapper<OtpCode>()
                .eq(OtpCode::getEmail, email)
                .eq(OtpCode::getPurpose, OtpCode.PURPOSE_LOGIN)
                .orderByDesc(OtpCode::getId);
    }

    /**
     * Failed attempts of the code being replaced, so that requesting a new code does not reset the budget.
     */
    private int inheritedAttempts(OtpCode previous, Instant now) {
        boolean recent = previous != null && previous.getUsedAt() == null
                && previous.getAttempts() < properties.maxAttempts()
                && previous.getCreatedAt().isAfter(now.minus(properties.lockDuration()));
        return recent ? previous.getAttempts() : 0;
    }

    private record CheckResult(OtpCode otp, ErrorCode error) {

        static CheckResult failed(ErrorCode error) {
            return new CheckResult(null, error);
        }
    }
}
