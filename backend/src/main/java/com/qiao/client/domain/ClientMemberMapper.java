package com.qiao.client.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClientMemberMapper extends EntityMapper<ClientMember> {
}
