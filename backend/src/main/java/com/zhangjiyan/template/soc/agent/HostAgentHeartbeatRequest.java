package com.zhangjiyan.template.soc.agent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record HostAgentHeartbeatRequest(
        @NotBlank @Size(max = 128) String agentId,
        @Size(max = 128) String hostname,
        @Pattern(regexp = "macos|windows|linux") String osType,
        @Size(max = 64) String agentVersion,
        @Size(max = 32) List<@Size(max = 64) String> ipAddresses,
        @Min(0) Integer queueDepth,
        @Min(0) Long queueBytes,
        @Min(0) Long collectedCount,
        @Min(0) Long sentCount,
        @Min(0) Long failedCount,
        @Min(0) Long uptimeSeconds,
        LocalDateTime observedAt
) {
}
