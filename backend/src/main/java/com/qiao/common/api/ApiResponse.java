package com.qiao.common.api;

/**
 * Envelope used by every JSON response: {@code {code, message, data}}.
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static final String OK = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, OK, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(OK, OK, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
