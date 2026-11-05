package com.qiao.account.service;

import com.qiao.account.domain.AppUser;
import com.qiao.account.domain.UserRole;
import com.qiao.account.web.MeView;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.security.AuthPrincipal;
import com.qiao.common.util.Locales;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.compliance.service.ConsentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The signed-in user's own account: profile summary, locale, data export and deletion.
 */
@Service
public class AccountService {

    private final UserService userService;
    private final ConsentService consentService;
    private final TokenDenylistService denylistService;
    private final AuditService auditService;
    private final List<OnboardingStatusProvider> statusProviders;
    private final List<PersonalDataHandler> dataHandlers;

    public AccountService(UserService userService, ConsentService consentService,
                          TokenDenylistService denylistService, AuditService auditService,
                          List<OnboardingStatusProvider> statusProviders, List<PersonalDataHandler> dataHandlers) {
        this.userService = userService;
        this.consentService = consentService;
        this.denylistService = denylistService;
        this.auditService = auditService;
        this.statusProviders = statusProviders;
        this.dataHandlers = dataHandlers;
    }

    public MeView me(long userId) {
        AppUser user = userService.get(userId);
        String onboarding = statusProviders.stream()
                .filter(p -> p.role() == user.getRole())
                .findFirst()
                .map(p -> p.onboardingStatus(userId))
                .orElse(null);
        Map<String, Boolean> consents = new LinkedHashMap<>();
        consentService.currentUnscoped(userId).forEach((type, granted) -> consents.put(type.name(), granted));
        return new MeView(user.getId(), user.getEmail(), user.getRole(), user.getStatus(), user.getLocale(),
                user.getRole() == UserRole.ENGINEER ? onboarding : null,
                user.getRole() == UserRole.CLIENT ? onboarding : null,
                consents);
    }

    public MeView updateLocale(long userId, String locale) {
        String normalized = Optional.ofNullable(locale).filter(Locales::isSupported)
                .orElseThrow(() -> ApiException.validation("locale", ValidationCodes.INVALID));
        userService.updateLocale(userId, normalized);
        return me(userId);
    }

    @Transactional
    public Map<String, Object> export(long userId) {
        AppUser user = userService.get(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("email", user.getEmail());
        account.put("role", user.getRole());
        account.put("status", user.getStatus());
        account.put("locale", user.getLocale());
        account.put("createdAt", user.getCreatedAt());
        account.put("lastLoginAt", user.getLastLoginAt());
        export.put("account", account);
        export.put("consents", consentService.history(userId));
        dataHandlers.forEach(handler -> export.put(handler.section(), handler.export(userId)));
        auditService.log(Actor.user(userId), AuditAction.ACCOUNT_DATA_EXPORTED, "app_user", userId);
        return export;
    }

    /**
     * Refused while a contract is active; otherwise anonymizes personal data and revokes the current token.
     */
    @Transactional
    public void delete(AuthPrincipal principal) {
        long userId = principal.id();
        dataHandlers.forEach(handler -> handler.assertDeletable(userId));
        dataHandlers.forEach(handler -> handler.anonymize(userId));
        userService.anonymize(userId);
        denylistService.revoke(principal);
        auditService.log(Actor.user(userId), AuditAction.ACCOUNT_DELETED, "app_user", userId);
    }
}
