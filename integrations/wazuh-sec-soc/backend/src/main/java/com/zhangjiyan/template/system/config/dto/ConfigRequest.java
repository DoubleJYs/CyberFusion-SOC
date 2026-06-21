package com.zhangjiyan.template.system.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfigRequest(
        @NotBlank @Size(max = 128) String configKey,
        @NotBlank @Size(max = 128) String configName,
        @NotNull @Size(max = 1000) String configValue,
        @NotBlank @Size(max = 32) String valueType,
        @NotBlank @Size(max = 64) String groupCode,
        Integer editable,
        Integer status,
        @Size(max = 255) String remark
) {
}
