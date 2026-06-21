package com.zhangjiyan.template.system.auth.dto;

import com.zhangjiyan.template.system.menu.dto.MenuTreeResponse;

import java.util.List;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        String tokenType,
        UserInfoResponse userInfo,
        List<String> roles,
        List<String> permissions,
        List<MenuTreeResponse> menus
) {
}
