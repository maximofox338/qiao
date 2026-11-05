package com.qiao.contract.service;

import java.time.Instant;

/**
 * A side's review of a contract, as shown to that side in the contract views.
 */
public record ReviewSummary(int stars, String comment, Instant createdAt) {
}
