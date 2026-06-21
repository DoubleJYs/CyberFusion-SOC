package com.zhangjiyan.template.soc.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CyberChefAnalysisRequest(
        @NotBlank @Size(max = 10000) String value,
        @Size(max = 64) String fieldName
) {
}
