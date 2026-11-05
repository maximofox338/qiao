package com.qiao.project.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceRequest(
        @NotNull Long milestoneId,
        @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal talentAmountUsd) {
}
