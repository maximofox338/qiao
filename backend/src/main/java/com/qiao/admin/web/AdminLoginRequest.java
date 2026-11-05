package com.qiao.admin.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password,
                                @Size(max = 10) String totp) {
}
