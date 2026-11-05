package com.qiao.common.error;

import org.springframework.http.HttpStatus;

/**
 * Error codes returned in the envelope {@code code} field. Frontends translate by code; the message is a fallback.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INVALID_STATE(HttpStatus.CONFLICT, "Operation not allowed in the current state"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),

    OTP_INVALID(HttpStatus.BAD_REQUEST, "Invalid code"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Code expired"),
    OTP_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes, try again later"),
    AUTH_ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "Role is required for new accounts"),
    TERMS_REQUIRED(HttpStatus.BAD_REQUEST, "Terms and privacy policy must be accepted"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    ADMIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts, account locked for 15 minutes"),
    TOTP_REQUIRED(HttpStatus.UNAUTHORIZED, "Authenticator code required"),
    TOTP_INVALID(HttpStatus.UNAUTHORIZED, "Invalid authenticator code"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already in use"),

    ACCOUNT_HAS_ACTIVE_CONTRACTS(HttpStatus.CONFLICT, "Account has active contracts"),
    CONSENT_REQUIRED(HttpStatus.CONFLICT, "Consent required"),
    DECLARATIONS_REQUIRED(HttpStatus.BAD_REQUEST, "All declarations must be accepted"),

    COMPANY_NOT_APPROVED(HttpStatus.CONFLICT, "Company not approved"),
    SANCTIONS_NOT_CLEAR(HttpStatus.BAD_REQUEST, "Approval requires a CLEAR sanctions screening"),
    PROHIBITED_CATEGORY(HttpStatus.BAD_REQUEST, "Category not allowed"),
    MILESTONES_REQUIRED(HttpStatus.BAD_REQUEST, "At least one milestone is required"),
    MILESTONE_SUM_MISMATCH(HttpStatus.BAD_REQUEST, "Milestone amounts must add up to the budget"),
    END_DATE_REQUIRED(HttpStatus.BAD_REQUEST, "End date is required"),
    DURATION_TOO_LONG(HttpStatus.BAD_REQUEST, "Projects cannot last longer than 12 months"),
    CATEGORY_NOT_ALLOWED(HttpStatus.CONFLICT, "The project category is no longer allowed"),
    PROJECT_NOT_OPEN(HttpStatus.CONFLICT, "Project is not open"),

    PROFILE_NOT_APPROVED(HttpStatus.CONFLICT, "Engineer profile not approved"),
    PARTNER_REF_REQUIRED(HttpStatus.BAD_REQUEST, "A partner reference is required to approve"),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "Already applied to this project"),
    CV_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "The engineer has not shared the full CV"),
    TRANSFER_LIMIT_REACHED(HttpStatus.CONFLICT, "Yearly cross-border transfer limit reached"),

    CONTRACT_NOT_ACTIVE(HttpStatus.CONFLICT, "Contract is not active"),
    MILESTONE_NOT_FUNDED(HttpStatus.CONFLICT, "Milestone not funded"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File larger than 20 MB"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "File type not supported"),

    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Payment provider error"),
    PAYMENT_PROVIDER_NOT_FAKE(HttpStatus.CONFLICT, "Test payments are not enabled"),
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT, "Already paid"),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "Invalid signature"),
    NOTHING_TO_SETTLE(HttpStatus.CONFLICT, "No accepted and paid milestones to settle"),

    ENGINEER_NOT_FOUND(HttpStatus.NOT_FOUND, "No approved engineer with that alias"),
    ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "Already a member of the team or invited"),
    TEAM_FULL(HttpStatus.CONFLICT, "A team has at most 10 members"),
    TEAM_LEAD_ONLY(HttpStatus.FORBIDDEN, "Only the team lead can do this"),
    TEAM_TOO_SMALL(HttpStatus.CONFLICT, "A team needs at least two active members to apply"),
    TEAM_HAS_ACTIVE_WORK(HttpStatus.CONFLICT, "The team has active applications or contracts"),
    SHARES_SUM_MISMATCH(HttpStatus.BAD_REQUEST, "Shares must add up to 100"),

    CONTRACT_NOT_COMPLETED(HttpStatus.CONFLICT, "Reviews open once the contract is completed"),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "This side already reviewed the contract"),

    TOTP_ALREADY_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is already enabled"),
    TOTP_NOT_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is not enabled"),
    TOTP_SETUP_REQUIRED(HttpStatus.CONFLICT, "Start the authenticator setup first"),
    TOTP_CODE_INVALID(HttpStatus.BAD_REQUEST, "Invalid or already used authenticator code");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
