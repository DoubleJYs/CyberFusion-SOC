package com.zhangjiyan.template.system.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String mobile,
        Long deptId,
        String deptName,
        Long postId,
        String postName,
        Integer status,
        List<Long> roleIds,
        List<String> roles,
        LocalDateTime createdAt
) {
}
