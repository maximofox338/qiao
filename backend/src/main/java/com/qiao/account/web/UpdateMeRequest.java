package com.qiao.account.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateMeRequest(@NotBlank String locale) {
}
