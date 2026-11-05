package com.qiao.project.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MilestoneRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String deliverable,
        @NotNull LocalDate dueDate,
        @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal amountUsd) {
}
