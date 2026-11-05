package com.qiao.account.service;

import com.qiao.account.domain.UserRole;

/**
 * Supplies the onboarding status shown by {@code GET /api/me} for users of one role
 * (engineer profile status, client company status).
 */
public interface OnboardingStatusProvider {

    UserRole role();

    String onboardingStatus(long userId);
}
