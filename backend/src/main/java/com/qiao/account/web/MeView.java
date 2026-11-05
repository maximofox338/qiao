package com.qiao.account.web;

import com.qiao.account.domain.UserRole;
import com.qiao.account.domain.UserStatus;

import java.util.Map;

/**
 * {@code engineerProfileStatus} is set for engineers, {@code companyStatus} for clients.
 */
public record MeView(Long id, String email, UserRole role, UserStatus status, String locale,
                     String engineerProfileStatus, String companyStatus, Map<String, Boolean> consents) {
}
