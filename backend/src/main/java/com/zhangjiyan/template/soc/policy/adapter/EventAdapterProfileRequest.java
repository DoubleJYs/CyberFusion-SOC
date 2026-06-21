package com.zhangjiyan.template.soc.policy.adapter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EventAdapterProfileRequest(
        @NotBlank @Pattern(regexp = "waf|zap|trivy|wazuh|suricata|zeek") String sourceType,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 500) String description,
        @Pattern(regexp = "draft|active|disabled") String status,
        Boolean enabled,
        @Min(0) @Max(10000) Integer sortOrder,
        @Size(max = 255) String sampleFile
) {
}
