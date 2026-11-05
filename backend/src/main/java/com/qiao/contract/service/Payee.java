package com.qiao.contract.service;

import com.qiao.team.domain.TeamRole;

import java.math.BigDecimal;

/**
 * Who is paid how much for a contract milestone: the engineer (100 %) or each team member with their share.
 */
public record Payee(Long engineerUserId, TeamRole role, BigDecimal sharePct, BigDecimal talentAmountUsd) {
}
