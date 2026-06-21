package com.zhangjiyan.template.system.file.dto;

import jakarta.validation.constraints.Size;

public record FileUploadRequest(
        @Size(max = 64) String bizType
) {
}
