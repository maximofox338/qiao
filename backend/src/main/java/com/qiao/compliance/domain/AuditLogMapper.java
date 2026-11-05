package com.qiao.compliance.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends EntityMapper<AuditLog> {
}
