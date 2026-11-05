package com.qiao.project.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import com.qiao.team.domain.TeamRole;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Member of a team application (snapshot taken when the lead applies) with their share of every milestone.
 */
@Getter
@Setter
@TableName("application_member")
public class ApplicationMember extends BaseEntity {
    private Long applicationId;
    private Long projectId;
    private Long engineerUserId;
    private TeamRole role;
    private BigDecimal sharePct;
}
