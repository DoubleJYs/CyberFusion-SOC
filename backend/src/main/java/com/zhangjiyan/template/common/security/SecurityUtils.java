package com.zhangjiyan.template.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<LoginUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return Optional.empty();
        }
        return Optional.of(loginUser);
    }

    public static String currentUsername() {
        return currentUser().map(LoginUser::username).orElse("anonymous");
    }
}
