package com.qiao.account.service;

import com.qiao.account.domain.AppUser;
import com.qiao.account.domain.OtpCode;
import com.qiao.account.domain.UserRole;
import com.qiao.account.domain.UserStatus;
import com.qiao.account.web.LoginResponse;
import com.qiao.account.web.OtpVerifyRequest;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.security.IssuedToken;
import com.qiao.common.security.JwtService;
import com.qiao.common.util.Locales;
import com.qiao.common.util.Texts;
import com.qiao.compliance.domain.ConsentType;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.compliance.service.ConsentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Passwordless login and registration for users (spec §4).
 */
@Service
public class AuthService {

    private final OtpService otpService;
    private final UserService userService;
    private final AccountService accountService;
    private final ConsentService consentService;
    private final TokenDenylistService denylistService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;

    public AuthService(OtpService otpService, UserService userService, AccountService accountService,
                       ConsentService consentService, TokenDenylistService denylistService, JwtService jwtService,
                       AuditService auditService, ApplicationEventPublisher events,
                       TransactionTemplate transactionTemplate) {
        this.otpService = otpService;
        this.userService = userService;
        this.accountService = accountService;
        this.consentService = consentService;
        this.denylistService = denylistService;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
    }

    public void requestCode(String email, String locale) {
        otpService.request(Texts.normalizeEmail(email), locale);
    }

    /**
     * Verifies the code, registering the user on first login. The code is only consumed once all registration
     * requirements are met, so the client can retry with {@code role}/{@code acceptTerms}.
     */
    public LoginResponse verify(OtpVerifyRequest request) {
        String email = Texts.normalizeEmail(request.email());
        OtpCode otp = otpService.check(email, request.code());
        AppUser existing = userService.findByEmail(email).orElse(null);
        if (existing != null && existing.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (existing == null) {
            if (request.role() == null) {
                throw new ApiException(ErrorCode.AUTH_ROLE_REQUIRED);
            }
            if (!Boolean.TRUE.equals(request.acceptTerms())) {
                throw new ApiException(ErrorCode.TERMS_REQUIRED);
            }
        }
        return transactionTemplate.execute(status -> {
            otpService.consume(otp);
            AppUser user = existing != null ? existing : register(email, request);
            userService.recordLogin(user.getId());
            auditService.log(Actor.user(user.getId()), AuditAction.USER_LOGIN, "app_user", user.getId());
            IssuedToken token = jwtService.issueUserToken(user.getId(), user.getRole().name());
            return new LoginResponse(token.token(), accountService.me(user.getId()));
        });
    }

    public void logout(AuthPrincipal principal) {
        denylistService.revoke(principal);
        auditService.log(Actor.of(principal), principal.isAdmin() ? AuditAction.ADMIN_LOGOUT : AuditAction.USER_LOGOUT,
                principal.isAdmin() ? "admin_user" : "app_user", principal.id());
    }

    private AppUser register(String email, OtpVerifyRequest request) {
        UserRole role = request.role();
        String defaultLocale = role == UserRole.ENGINEER ? Locales.ZH_CN : Locales.EN;
        AppUser user = userService.create(email, role, Locales.normalize(request.locale(), defaultLocale));
        consentService.record(user.getId(), ConsentType.TERMS, true, null, request.termsVersion());
        consentService.record(user.getId(), ConsentType.PRIVACY, true, null, request.privacyVersion());
        events.publishEvent(new UserRegisteredEvent(user.getId(), role));
        auditService.log(Actor.user(user.getId()), AuditAction.USER_REGISTERED, "app_user", user.getId(),
                Map.of("role", role.name()));
        return user;
    }
}
