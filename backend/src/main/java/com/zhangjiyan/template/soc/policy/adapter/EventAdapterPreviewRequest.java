package com.zhangjiyan.template.soc.policy.adapter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventAdapterPreviewRequest(
        @NotBlank @Size(max = 200000) String payload
) {
}
