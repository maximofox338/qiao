package com.qiao.compliance.web;

import com.qiao.compliance.domain.ActorType;
import com.qiao.compliance.domain.AuditLog;

import java.time.Instant;
import java.util.Map;

public record AuditLogView(Long id, ActorType actorType, Long actorId, String action, String entityType,
                           String entityId, Map<String, Object> detail, String ip, Instant createdAt) {

    public static AuditLogView from(AuditLog log) {
        return new AuditLogView(log.getId(), log.getActorType(), log.getActorId(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getDetail(), log.getIp(), log.getCreatedAt());
    }
}
