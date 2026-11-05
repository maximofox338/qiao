package com.qiao.account.web;

import com.qiao.account.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        UserRole role,
        String locale,
        Boolean acceptTerms,
        @Size(max = 20) String termsVersion,
        @Size(max = 20) String privacyVersion) {
}
