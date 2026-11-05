package com.qiao.project.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qiao.common.persistence.BaseEntity;
import com.qiao.compliance.service.KeywordFlag;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A client project with milestones. Deliberately has no salary, hours or office fields (spec §1.1).
 */
@Getter
@Setter
@TableName(value = "project", autoResultMap = true)
public class Project extends BaseEntity {
    private Long companyId;
    private String title;
    private String description;
    private String categoryCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> skills;
    private BigDecimal budgetUsd;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectStatus status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<KeywordFlag> flags;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ProjectDeclarations declarations;
    private String reviewNote;
    private Long reviewedBy;
    private Instant reviewedAt;
}
