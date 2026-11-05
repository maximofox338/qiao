package com.qiao.contract.service;

import com.qiao.account.domain.UserRole;
import com.qiao.contract.domain.Contract;

/**
 * A contract seen by one of its parties; {@code side} is CLIENT for company members, ENGINEER for the engineer.
 */
public record ContractParticipant(Contract contract, UserRole side) {
}
