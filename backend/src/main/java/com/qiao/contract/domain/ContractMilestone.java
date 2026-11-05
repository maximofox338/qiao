package com.qiao.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@TableName("contract_milestone")
public class ContractMilestone extends BaseEntity {
    private Long contractId;
    private Integer seq;
    private String title;
    private String deliverable;
    private LocalDate dueDate;
    private BigDecimal talentAmountUsd;
    private BigDecimal clientAmountUsd;
    private MilestoneStatus status;
    private String submittedNote;
    private Instant submittedAt;
    private Instant acceptedAt;
    private String revisionReason;
    private String disputeReason;
    private Long settledLineId;
}
