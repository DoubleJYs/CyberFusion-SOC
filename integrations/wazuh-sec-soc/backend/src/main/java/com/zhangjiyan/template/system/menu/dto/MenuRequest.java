package com.zhangjiyan.template.system.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuRequest(
        @NotNull Long parentId,
        @NotBlank String name,
        String path,
        String component,
        String icon,
        @NotBlank String type,
        String permission,
        Integer sort,
        Integer visible,
        Integer status
) {
}
