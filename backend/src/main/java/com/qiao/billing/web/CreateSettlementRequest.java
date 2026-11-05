package com.qiao.billing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateSettlementRequest(@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String period) {
}
