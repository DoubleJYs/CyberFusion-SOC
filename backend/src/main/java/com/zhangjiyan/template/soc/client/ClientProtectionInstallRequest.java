package com.zhangjiyan.template.soc.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** User-editable, bounded settings for installing an Agent on the current backend host. */
public record ClientProtectionInstallRequest(
        @NotBlank @Size(max = 64) String assetIp,
        @NotBlank @Size(max = 64) String agentVersion,
        @NotBlank @Pattern(regexp = "full|host-log|patrol-audit|file-integrity|baseline-audit") String profile,
        @NotBlank @Size(max = 512) String fimPath,
        @NotBlank @Pattern(regexp = "[1-9][0-9]*(ms|s|m|h)") String interval
) {
}
