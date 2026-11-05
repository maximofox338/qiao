package com.qiao.account.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends EntityMapper<AppUser> {
}
