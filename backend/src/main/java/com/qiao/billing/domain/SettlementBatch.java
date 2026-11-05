package com.qiao.billing.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Monthly batch of engineer payouts handed to the PRC partner, who pays in RMB and withholds IIT.
 */
@Getter
@Setter
@TableName("settlement_batch")
public class SettlementBatch extends BaseEntity {
    private String period;
    private SettlementBatchStatus status;
    private Long createdByAdminId;
    private Instant exportedAt;
}
