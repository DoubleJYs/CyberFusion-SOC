package com.zhangjiyan.template.soc.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoWorkflowLogRequest(
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(max = 40) String time,
        @NotBlank @Size(max = 40) String type,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 500) String detail,
        @Size(max = 32) String stepKey
) {
}
