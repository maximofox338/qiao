package com.qiao.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/admin/me/password}: same policy as the bootstrap and created admins (12–200 characters, bcrypt).
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(min = 12, max = 200) String newPassword) {
}
