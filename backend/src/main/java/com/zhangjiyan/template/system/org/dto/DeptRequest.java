package com.zhangjiyan.template.system.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeptRequest(
        Long parentId,
        @NotBlank @Size(max = 64) String deptName,
        @NotBlank @Size(max = 64) String deptCode,
        @Size(max = 64) String leader,
        @Size(max = 32) String phone,
        Integer sort,
        Integer status
) {
}
