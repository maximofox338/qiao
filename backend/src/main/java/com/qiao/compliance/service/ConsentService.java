package com.qiao.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ValidationCodes;
import com.qiao.common.util.Texts;
import com.qiao.common.web.RequestMeta;
import com.qiao.compliance.domain.Consent;
import com.qiao.compliance.domain.ConsentMapper;
import com.qiao.compliance.domain.ConsentType;
import com.qiao.compliance.web.ConsentView;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Records consents with version, timestamp, IP and user agent (spec §1.8).
 */
@Service
public class ConsentService {

    private final ConsentMapper consentMapper;
    private final LegalService legalService;
    private final AuditService auditService;

    public ConsentService(ConsentMapper consentMapper, LegalService legalService, AuditService auditService) {
        this.consentMapper = consentMapper;
        this.legalService = legalService;
        this.auditService = auditService;
    }

    /**
     * @param version accepted version, or {@code null} for the current version of the related legal text
     */
    public ConsentView record(long userId, ConsentType type, boolean granted, String scopeRef, String version) {
        String scope = Texts.trimToNull(scopeRef);
        if (type.scoped() && scope == null) {
            throw ApiException.validation("scopeRef", ValidationCodes.REQUIRED);
        }
        if (!type.scoped() && scope != null) {
            throw ApiException.validation("scopeRef", ValidationCodes.INVALID);
        }
        String acceptedVersion = Texts.trimToNull(version);
        if (acceptedVersion != null && acceptedVersion.length() > 20) {
            throw ApiException.validation("version", ValidationCodes.SIZE);
        }
        RequestMeta meta = RequestMeta.current();
        Consent consent = new Consent();
        consent.setUserId(userId);
        consent.setType(type);
        consent.setVersion(acceptedVersion != null ? acceptedVersion : legalService.currentVersion(type.document()));
        consent.setGranted(granted);
        consent.setScopeRef(scope);
        consent.setIp(meta.ip());
        consent.setUserAgent(meta.userAgent());
        consentMapper.insert(consent);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", type.name());
        detail.put("granted", granted);
        detail.put("version", consent.getVersion());
        if (scope != null) {
            detail.put("scopeRef", scope);
        }
        auditService.log(Actor.user(userId), AuditAction.CONSENT_CHANGED, "consent", consent.getId(), detail);
        return ConsentView.from(consent);
    }

    public boolean isGranted(long userId, ConsentType type, String scopeRef) {
        Consent latest = consentMapper.selectOne(new LambdaQueryWrapper<Consent>()
                .eq(Consent::getUserId, userId)
                .eq(Consent::getType, type)
                .eq(scopeRef != null, Consent::getScopeRef, scopeRef)
                .isNull(scopeRef == null, Consent::getScopeRef)
                .orderByDesc(Consent::getId)
                .last("LIMIT 1"));
        return latest != null && Boolean.TRUE.equals(latest.getGranted());
    }

    /**
     * Users (among {@code userIds}) whose latest consent of {@code type} for {@code scopeRef} is granted.
     */
    public Set<Long> usersWithGrant(Collection<Long> userIds, ConsentType type, String scopeRef) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        List<Consent> rows = consentMapper.selectList(new LambdaQueryWrapper<Consent>()
                .in(Consent::getUserId, userIds)
                .eq(Consent::getType, type)
                .eq(Consent::getScopeRef, scopeRef)
                .orderByDesc(Consent::getId));
        Set<Long> seen = new HashSet<>();
        Set<Long> granted = new HashSet<>();
        for (Consent row : rows) {
            if (seen.add(row.getUserId()) && Boolean.TRUE.equals(row.getGranted())) {
                granted.add(row.getUserId());
            }
        }
        return granted;
    }

    /**
     * Current state of every unscoped consent type ({@code false} when never recorded).
     */
    public Map<ConsentType, Boolean> currentUnscoped(long userId) {
        Map<ConsentType, Boolean> state = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            if (!type.scoped()) {
                state.put(type, false);
            }
        }
        latest(userId).stream()
                .filter(view -> view.scopeRef() == null)
                .forEach(view -> state.put(view.type(), view.granted()));
        return state;
    }

    /**
     * Latest consent per (type, scope).
     */
    public List<ConsentView> latest(long userId) {
        Map<String, ConsentView> latest = new LinkedHashMap<>();
        consentMapper.selectList(new LambdaQueryWrapper<Consent>()
                        .eq(Consent::getUserId, userId)
                        .orderByDesc(Consent::getId))
                .forEach(c -> latest.putIfAbsent(c.getType() + "|" + Objects.toString(c.getScopeRef(), ""), ConsentView.from(c)));
        return List.copyOf(latest.values());
    }

    public List<ConsentView> history(long userId) {
        return consentMapper.selectList(new LambdaQueryWrapper<Consent>()
                        .eq(Consent::getUserId, userId)
                        .orderByAsc(Consent::getId))
                .stream().map(ConsentView::from).toList();
    }
}
