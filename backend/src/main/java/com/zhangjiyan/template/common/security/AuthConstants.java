package com.zhangjiyan.template.common.security;

public final class AuthConstants {
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_ISSUER = "template-001-springboot-vue-admin";
    public static final String REFRESH_TOKEN_COOKIE = "template_refresh_token";

    private AuthConstants() {
    }
}
