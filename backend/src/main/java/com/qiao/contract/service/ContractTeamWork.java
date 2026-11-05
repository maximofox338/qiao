package com.qiao.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractMapper;
import com.qiao.contract.domain.ContractMember;
import com.qiao.contract.domain.ContractMemberMapper;
import com.qiao.contract.domain.ContractStatus;
import com.qiao.team.service.TeamWorkDirectory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A team (member) is committed while one of its contracts is PENDING_SIGNATURES or ACTIVE.
 */
@Component
public class ContractTeamWork implements TeamWorkDirectory {

    private final ContractMapper contractMapper;
    private final ContractMemberMapper memberMapper;

    public ContractTeamWork(ContractMapper contractMapper, ContractMemberMapper memberMapper) {
        this.contractMapper = contractMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public boolean hasActiveWork(long teamId, Long engineerUserId) {
        List<Long> open = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                        .select(Contract::getId)
                        .eq(Contract::getTeamId, teamId)
                        .in(Contract::getStatus, ContractStatus.PENDING_SIGNATURES, ContractStatus.ACTIVE))
                .stream().map(Contract::getId).toList();
        if (open.isEmpty() || engineerUserId == null) {
            return !open.isEmpty();
        }
        return memberMapper.exists(new LambdaQueryWrapper<ContractMember>()
                .in(ContractMember::getContractId, open)
                .eq(ContractMember::getEngineerUserId, engineerUserId));
    }
}
