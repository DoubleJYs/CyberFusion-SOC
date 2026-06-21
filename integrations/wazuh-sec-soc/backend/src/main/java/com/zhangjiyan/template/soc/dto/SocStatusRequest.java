package com.zhangjiyan.template.soc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocStatusRequest(
        @NotBlank @Size(max = 32) String targetStatus,
        @Size(max = 500) String remark
) {
}
