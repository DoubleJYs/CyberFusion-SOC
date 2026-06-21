package com.zhangjiyan.template.soc.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CyberFusionImportRequest(
        @NotBlank
        @Pattern(regexp = "wazuh|zeek|suricata|trivy|misp|zap|sigma|shuffle|falco|opencti|osquery|velociraptor|cowrie|waf")
        String sourceType,
        @NotBlank @Size(max = 200000) String content,
        Boolean linkAlerts
) {
}
