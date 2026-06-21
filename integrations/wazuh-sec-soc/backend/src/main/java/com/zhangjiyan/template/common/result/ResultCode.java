package com.zhangjiyan.template.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS("SUCCESS", "success"),
    BAD_REQUEST("BAD_REQUEST", "bad request"),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "invalid username or password"),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "token expired"),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "unauthorized"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "forbidden"),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "too many requests"),
    USER_DISABLED("USER_DISABLED", "user disabled"),
    NOT_FOUND("NOT_FOUND", "not found"),
    VALIDATION_ERROR("VALIDATION_ERROR", "validation error"),
    BUSINESS_ERROR("BUSINESS_ERROR", "business error"),
    INTERNAL_ERROR("INTERNAL_ERROR", "internal server error");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
