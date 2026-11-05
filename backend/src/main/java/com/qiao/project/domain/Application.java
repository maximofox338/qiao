package com.qiao.project.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@TableName(value = "application", autoResultMap = true)
public class Application extends BaseEntity {
    private Long projectId;
    /**
     * The applicant; for a team application, the team lead.
     */
    private Long engineerUserId;
    /**
     * Set for team applications (members in {@link ApplicationMember}).
     */
    private Long teamId;
    private String coverNote;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<MilestonePrice> prices;
    private BigDecimal talentTotalUsd;
    private BigDecimal clientTotalUsd;
    private ApplicationStatus status;
}
