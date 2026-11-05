package com.qiao.client.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeclarationsRequest(@NotBlank @Size(max = 20) String version, @NotNull Boolean accepted) {
}
