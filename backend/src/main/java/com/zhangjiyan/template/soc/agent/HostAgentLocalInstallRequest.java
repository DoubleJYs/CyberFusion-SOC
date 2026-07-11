package com.zhangjiyan.template.soc.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Inputs permitted for the local host installation workflow. Runtime paths,
 * target operating system, and API address are always derived server-side.
 */
public record HostAgentLocalInstallRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,128}") String agentId,
        @NotBlank @Size(max = 128) String agentName,
        @NotBlank @Size(max = 128) String hostname,
        @NotBlank @Size(max = 64) String agentVersion,
        @NotBlank @Pattern(regexp = "full|host-log|patrol-audit|file-integrity|baseline-audit") String profile,
        @Size(max = 32) List<@Size(max = 64) String> ipAddresses,
        @NotBlank @Size(max = 512) String fimPath,
        @NotBlank @Pattern(regexp = "[1-9][0-9]*(ms|s|m|h)") String interval
) {
}
