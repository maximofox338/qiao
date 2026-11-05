package com.qiao.project.domain;

import java.math.BigDecimal;

/**
 * Engineer's proposed price for one project milestone.
 */
public record MilestonePrice(Long milestoneId, BigDecimal talentAmountUsd) {
}
