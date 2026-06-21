package com.zhangjiyan.template.system.role.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RoleResponse(
        Long id,
        String roleCode,
        String roleName,
        String dataScope,
        Integer status,
        List<Long> menuIds,
        List<Long> deptIds,
        LocalDateTime createdAt
) {
}
