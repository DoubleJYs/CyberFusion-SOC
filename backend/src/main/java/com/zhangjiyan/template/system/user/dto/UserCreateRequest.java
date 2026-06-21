package com.zhangjiyan.template.system.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank(message = "不能为空") String username,
        @NotBlank(message = "不能为空") String nickname,
        @NotBlank(message = "不能为空") String password,
        String email,
        String mobile,
        Long deptId,
        Long postId,
        Integer status,
        java.util.List<Long> roleIds
) {
}
