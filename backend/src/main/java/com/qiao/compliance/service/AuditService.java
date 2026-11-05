package com.qiao.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.security.PartnerKeyFilter;
import com.qiao.common.web.RequestMeta;
import com.qiao.compliance.domain.ActorType;
import com.qiao.compliance.domain.AuditLog;
import com.qiao.compliance.domain.AuditLogMapper;
import com.qiao.compliance.web.AuditLogView;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only audit trail (spec §1.10). Entries join the caller's transaction. Partner entries of a request
 * authenticated with {@code PARTNER_API_KEY_PREVIOUS} get {@code partnerKey=PREVIOUS} and a {@code warning} in the
 * detail, so the operator can see whether the partner still uses the old key before finishing a rotation.
 */
@Service
public class AuditService {

    static final String PREVIOUS_KEY_WARNING = "Authenticated with PARTNER_API_KEY_PREVIOUS: the partner has not "
            + "switched to the new key yet; do not finish the rotation";

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void log(Actor actor, AuditAction action, String entityType, Object entityId, Map<String, ?> detail) {
        AuditLog entry = new AuditLog();
        entry.setActorType(actor.type());
        entry.setActorId(actor.id());
        entry.setAction(action.name());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        Map<String, Object> details = detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail);
        if (actor.type() == ActorType.PARTNER && PartnerKeyFilter.previousKeyUsed()) {
            details.put("partnerKey", "PREVIOUS");
            details.put("warning", PREVIOUS_KEY_WARNING);
        }
        entry.setDetail(details.isEmpty() ? null : Map.copyOf(details));
        entry.setIp(RequestMeta.current().ip());
        auditLogMapper.insert(entry);
    }

    public void log(Actor actor, AuditAction action, String entityType, Object entityId) {
        log(actor, action, entityType, entityId, null);
    }

    public PageResult<AuditLogView> search(String action, ActorType actorType, PageQuery pageQuery) {
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<AuditLog>()
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .eq(actorType != null, AuditLog::getActorType, actorType)
                .orderByDesc(AuditLog::getId);
        return PageResult.of(auditLogMapper.selectPage(pageQuery.toPage(), query), AuditLogView::from);
    }
}
