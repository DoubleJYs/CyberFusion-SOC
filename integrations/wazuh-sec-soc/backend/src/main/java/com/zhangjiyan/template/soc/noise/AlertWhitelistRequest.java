package com.zhangjiyan.template.soc.noise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AlertWhitelistRequest(
        @NotBlank @Size(max = 128) String ruleName,
        @Size(max = 64) String ruleId,
        @Size(max = 64) String assetIp,
        @Size(max = 64) String sourceIp,
        @Size(max = 32) String severity,
        @NotBlank @Size(max = 500) String reason,
        Integer enabled,
        LocalDateTime expiresAt
) {
}
