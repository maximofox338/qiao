package com.qiao.project.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Client-side budget split of a project; amounts add up to {@code project.budgetUsd}.
 */
@Getter
@Setter
@TableName("project_milestone")
public class ProjectMilestone extends BaseEntity {
    private Long projectId;
    private Integer seq;
    private String title;
    private String deliverable;
    private LocalDate dueDate;
    private BigDecimal amountUsd;
}
