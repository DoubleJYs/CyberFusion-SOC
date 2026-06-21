package com.zhangjiyan.template.system.auth.dto;

public record UserInfoResponse(Long id, String username, String nickname, String email, String mobile, Integer status) {
}
