package com.qiao.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.common.money.ShareSplitter;
import com.qiao.contract.domain.Contract;
import com.qiao.contract.domain.ContractMember;
import com.qiao.contract.domain.ContractMemberMapper;
import com.qiao.team.domain.TeamRole;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Engineer side of a contract: the single engineer, or every member of a team contract (lead first). Depends only
 * on its mapper so that every contract and billing service can use it.
 */
@Component
public class ContractParties {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final Comparator<ContractMember> LEAD_FIRST =
            Comparator.comparing((ContractMember m) -> m.getRole() != TeamRole.LEAD).thenComparing(ContractMember::getId);

    private final ContractMemberMapper memberMapper;

    public ContractParties(ContractMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    public List<ContractMember> members(Contract contract) {
        if (contract.getTeamId() == null) {
            return List.of();
        }
        return memberMapper.selectList(new LambdaQueryWrapper<ContractMember>()
                        .eq(ContractMember::getContractId, contract.getId()))
                .stream().sorted(LEAD_FIRST).toList();
    }

    public Map<Long, List<ContractMember>> membersByContract(Collection<Contract> contracts) {
        List<Long> teamContracts = contracts.stream().filter(c -> c.getTeamId() != null).map(Contract::getId).toList();
        if (teamContracts.isEmpty()) {
            return Map.of();
        }
        return memberMapper.selectList(new LambdaQueryWrapper<ContractMember>()
                        .in(ContractMember::getContractId, teamContracts))
                .stream().sorted(LEAD_FIRST).collect(Collectors.groupingBy(ContractMember::getContractId));
    }

    /**
     * User ids of the engineer side, lead first.
     */
    public List<Long> engineerUserIds(Contract contract) {
        if (contract.getTeamId() == null) {
            return List.of(contract.getEngineerUserId());
        }
        return members(contract).stream().map(ContractMember::getEngineerUserId).toList();
    }

    public boolean isEngineer(Contract contract, long userId) {
        return contract.getEngineerUserId() == userId
                || (contract.getTeamId() != null && memberMapper.exists(new LambdaQueryWrapper<ContractMember>()
                .eq(ContractMember::getContractId, contract.getId())
                .eq(ContractMember::getEngineerUserId, userId)));
    }

    /**
     * Ids of the team contracts the engineer is a member of.
     */
    public Set<Long> contractIdsOfMember(long userId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ContractMember>()
                        .eq(ContractMember::getEngineerUserId, userId))
                .stream().map(ContractMember::getContractId).collect(Collectors.toSet());
    }

    /**
     * Splits a milestone's talent amount among the payees ({@link ShareSplitter}); {@code members} empty means the
     * single engineer of the contract.
     */
    public static List<Payee> payees(Contract contract, List<ContractMember> members, BigDecimal talentAmountUsd) {
        if (members.isEmpty()) {
            return List.of(new Payee(contract.getEngineerUserId(), null, HUNDRED, talentAmountUsd));
        }
        List<BigDecimal> parts = ShareSplitter.split(talentAmountUsd, members.stream().map(ContractMember::getSharePct).toList());
        return IntStream.range(0, members.size())
                .mapToObj(i -> new Payee(members.get(i).getEngineerUserId(), members.get(i).getRole(),
                        members.get(i).getSharePct(), parts.get(i)))
                .toList();
    }
}
