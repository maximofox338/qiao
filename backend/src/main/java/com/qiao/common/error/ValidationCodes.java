package com.qiao.common.error;

/**
 * Per-field codes inside {@code VALIDATION_ERROR} payloads ({@code data.fields.<field> = <code>}). Bean Validation
 * failures use the constraint name ({@code NotBlank}, {@code Size}, {@code DecimalMin}, ...); the service-level
 * checks use the constants below.
 */
public final class ValidationCodes {

    public static final String REQUIRED = "REQUIRED";
    public static final String INVALID = "INVALID";
    public static final String SIZE = "Size";
    public static final String END_BEFORE_START = "END_BEFORE_START";
    public static final String DUE_OUTSIDE_RANGE = "DUE_OUTSIDE_RANGE";

    private ValidationCodes() {
    }
}
