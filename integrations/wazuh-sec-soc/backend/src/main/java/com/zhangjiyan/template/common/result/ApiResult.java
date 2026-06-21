package com.zhangjiyan.template.common.result;

import java.time.OffsetDateTime;

public record ApiResult<T>(String code, String message, T data, String timestamp) {

    public static <T> ApiResult<T> ok(T data) {
        return of(ResultCode.SUCCESS, ResultCode.SUCCESS.getMessage(), data);
    }

    public static ApiResult<Void> ok() {
        return of(ResultCode.SUCCESS, ResultCode.SUCCESS.getMessage(), null);
    }

    public static ApiResult<Void> fail(ResultCode resultCode, String message) {
        return of(resultCode, message, null);
    }

    public static <T> ApiResult<T> of(ResultCode resultCode, String message, T data) {
        return new ApiResult<>(resultCode.getCode(), message, data, OffsetDateTime.now().toString());
    }
}
