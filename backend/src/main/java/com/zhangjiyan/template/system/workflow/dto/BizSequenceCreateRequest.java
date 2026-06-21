package com.zhangjiyan.template.system.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BizSequenceCreateRequest(
        @NotBlank @Size(max = 64) String sequenceCode,
        @NotBlank @Size(max = 128) String sequenceName,
        @Size(max = 32) String prefix,
        @Size(max = 32) String datePattern,
        Long currentValue,
        @NotNull @Min(1) Integer step,
        @NotNull @Min(1) Integer length,
        @NotBlank String resetPolicy,
        Integer enabled,
        @Size(max = 255) String remark
) {
}
