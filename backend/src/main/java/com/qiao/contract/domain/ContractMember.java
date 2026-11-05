package com.qiao.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qiao.common.persistence.BaseEntity;
import com.qiao.team.domain.TeamRole;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Member of a team contract with the share of every milestone fixed at hire time.
 */
@Getter
@Setter
@TableName("contract_member")
public class ContractMember extends BaseEntity {
    private Long contractId;
    private Long engineerUserId;
    private TeamRole role;
    private BigDecimal sharePct;
}
