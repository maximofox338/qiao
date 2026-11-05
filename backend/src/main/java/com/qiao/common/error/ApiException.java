package com.qiao.common.error;

import java.util.Map;

/**
 * Business error carrying an {@link ErrorCode}; rendered by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object data;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public static ApiException notFound(String entity) {
        return new ApiException(ErrorCode.NOT_FOUND, entity + " not found");
    }

    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE, message);
    }

    public static ApiException validation(String field, String fieldCode) {
        return validation(Map.of(field, fieldCode));
    }

    public static ApiException validation(Map<String, String> fields) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of("fields", fields));
    }

    public ErrorCode code() {
        return code;
    }

    public Object data() {
        return data;
    }
}
