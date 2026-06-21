package com.zhangjiyan.template.system.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UserUpdateRequest(
        @NotBlank String nickname,
        String email,
        String mobile,
        Long deptId,
        Long postId,
        Integer status,
        List<Long> roleIds
) {
}
