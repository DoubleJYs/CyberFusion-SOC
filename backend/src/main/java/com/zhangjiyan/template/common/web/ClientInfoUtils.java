package com.zhangjiyan.template.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientInfoUtils {

    private ClientInfoUtils() {
    }

    public static String ip(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null ? "" : userAgent;
    }
}
