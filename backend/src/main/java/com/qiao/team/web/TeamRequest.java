package com.qiao.team.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description) {
}
