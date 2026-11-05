package com.qiao.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotNull Boolean allowed,
        @NotBlank @Size(max = 120) String nameEn,
        @NotBlank @Size(max = 120) String nameEs,
        @NotBlank @Size(max = 120) String nameZh) {
}
