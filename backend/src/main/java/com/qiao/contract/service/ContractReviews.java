package com.qiao.contract.service;

import com.qiao.account.domain.UserRole;

import java.util.Optional;

/**
 * Reviews of a contract for the contract views. Implemented by the review module (which owns the table) using only
 * its mapper, to avoid a bean cycle with {@link ContractService}.
 */
public interface ContractReviews {

    Optional<ReviewSummary> byContractAndSide(long contractId, UserRole fromSide);
}
