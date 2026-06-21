package com.zhangjiyan.template.soc.policy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LocalCheckCommandRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String commandKey,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Pattern(regexp = "Linux|macOS|Windows") String osType,
        @NotBlank @Size(max = 64) String category,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 1000) String commandArgvJson,
        @Min(1) @Max(30) Integer timeoutSeconds,
        @Min(1) @Max(256) Integer outputLimitKb,
        Boolean enabled,
        @Pattern(regexp = "draft|active|disabled") String status,
        @Min(0) @Max(10000) Integer sortOrder,
        @Size(max = 500) String safetyNote
) {
}
