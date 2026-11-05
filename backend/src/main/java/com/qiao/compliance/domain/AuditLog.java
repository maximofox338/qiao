package com.qiao.compliance.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@TableName(value = "audit_log", autoResultMap = true)
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private ActorType actorType;
    private Long actorId;
    private String action;
    private String entityType;
    private String entityId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> detail;
    private String ip;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
