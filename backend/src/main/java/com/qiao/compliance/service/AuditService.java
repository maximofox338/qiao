package com.qiao.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.common.api.PageQuery;
import com.qiao.common.api.PageResult;
import com.qiao.common.web.RequestMeta;
import com.qiao.compliance.domain.ActorType;
import com.qiao.compliance.domain.AuditLog;
import com.qiao.compliance.domain.AuditLogMapper;
import com.qiao.compliance.web.AuditLogView;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Append-only audit trail (spec §1.10). Entries join the caller's transaction.
 */
@Service
public class AuditService {

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
        entry.setDetail(detail == null || detail.isEmpty() ? null : Map.copyOf(detail));
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
