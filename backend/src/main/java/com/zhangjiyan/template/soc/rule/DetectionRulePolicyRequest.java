package com.zhangjiyan.template.soc.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DetectionRulePolicyRequest(
        @NotBlank @Pattern(regexp = "^(sigma|waf|zap|suricata|wazuh|zeek|host)$") String sourceType,
        @NotBlank @Size(max = 160) String ruleId,
        @NotBlank @Size(max = 255) String ruleName,
        @NotBlank @Pattern(regexp = "^(identity|host|network|web|vulnerability|custom)$") String detectionCategory,
        @NotBlank @Pattern(regexp = "^(low|medium|high|critical)$") String severity,
        @Size(max = 1000) String detectionSummary,
        @Pattern(regexp = "^(draft|active|disabled)$") String status,
        Boolean enabled
) {
}
