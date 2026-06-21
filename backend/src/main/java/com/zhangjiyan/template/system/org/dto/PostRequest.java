package com.zhangjiyan.template.system.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequest(
        @NotBlank @Size(max = 64) String postCode,
        @NotBlank @Size(max = 64) String postName,
        Integer sort,
        Integer status,
        @Size(max = 255) String remark
) {
}
