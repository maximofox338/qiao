package com.qiao.team.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteRequest(@NotBlank @Size(max = 16) String alias) {
}
