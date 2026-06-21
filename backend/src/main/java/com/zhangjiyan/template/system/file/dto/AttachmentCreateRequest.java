package com.zhangjiyan.template.system.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttachmentCreateRequest(
        @NotBlank @Size(max = 64) String bizType,
        @NotBlank @Size(max = 64) String bizId,
        @NotNull Long fileId,
        Integer sortOrder,
        @Size(max = 255) String remark
) {
}
