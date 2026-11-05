package com.qiao.team.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TeamMemberMapper extends EntityMapper<TeamMember> {
}
