package com.qiao.team.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("team_member")
public class TeamMember extends BaseEntity {
    private Long teamId;
    private Long userId;
    private TeamRole role;
    private TeamMemberStatus status;
    private Long invitedByUserId;
    private Instant respondedAt;
}
