package com.qiao.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpCodeRequest(@NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*") String code) {
}
