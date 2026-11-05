package com.qiao.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.qiao.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps every exception to the response envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Object>> handleApi(ApiException ex) {
        return respond(ex.code(), ex.getMessage(), ex.data());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiResponse<Object>> handleBind(BindException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            fields.putIfAbsent(error.getField(), fieldCode(error.getCode(), error.isBindingFailure()));
        }
        ex.getGlobalErrors().forEach(e -> fields.putIfAbsent(e.getObjectName(), ValidationCodes.INVALID));
        return validation(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String[] codes = error.getCodes();
                String constraint = codes == null || codes.length == 0 ? null : codes[codes.length - 1];
                fields.putIfAbsent(name, fieldCode(constraint, false));
            }
        });
        return validation(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = ex.getConstraintViolations().stream().collect(Collectors.toMap(
                v -> lastSegment(v.getPropertyPath().toString()),
                v -> fieldCode(v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(), false),
                (a, b) -> a, LinkedHashMap::new));
        return validation(fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String path = mapping.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining(".")).replace(".[", "[");
            fields.put(path, ValidationCodes.INVALID);
        }
        return validation(fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation(Map.of(ex.getName(), ValidationCodes.INVALID));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return validation(Map.of(ex.getParameterName(), ValidationCodes.REQUIRED));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return validation(Map.of(ex.getRequestPartName(), ValidationCodes.REQUIRED));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return respond(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.defaultMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Object>> handleDuplicate(DuplicateKeyException ex) {
        log.info("Duplicate key: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.INVALID_STATE, "Concurrent or duplicate modification", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
    }

    private static ResponseEntity<ApiResponse<Object>> validation(Map<String, String> fields) {
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("fields", fields));
    }

    private static ResponseEntity<ApiResponse<Object>> respond(ErrorCode code, String message, Object data) {
        HttpStatus status = code.status();
        return ResponseEntity.status(status).body(ApiResponse.error(code.name(), message, data));
    }

    private static String fieldCode(String constraint, boolean bindingFailure) {
        return bindingFailure || constraint == null ? ValidationCodes.INVALID : constraint;
    }

    private static String lastSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
