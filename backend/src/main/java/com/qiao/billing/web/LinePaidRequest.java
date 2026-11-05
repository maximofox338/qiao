package com.qiao.billing.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record LinePaidRequest(
        @NotBlank @Size(max = 128) String partnerPaymentRef,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal iitWithheldCny,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal netPaidCny,
        @NotNull Instant paidAt) {
}
