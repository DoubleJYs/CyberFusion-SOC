package com.zhangjiyan.template.system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BizNoGenerateRequest(
        @NotBlank @Size(max = 64) String sequenceCode
) {
}
