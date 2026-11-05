package com.qiao.admin.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends EntityMapper<AdminUser> {
}
