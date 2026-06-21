package com.zhangjiyan.template.system.role.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RoleRequest(
        @NotBlank String roleCode,
        @NotBlank String roleName,
        String dataScope,
        Integer status,
        List<Long> menuIds,
        List<Long> deptIds
) {
}
