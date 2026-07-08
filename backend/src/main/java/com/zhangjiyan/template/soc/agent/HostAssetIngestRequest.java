package com.zhangjiyan.template.soc.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record HostAssetIngestRequest(
        @NotBlank @Size(max = 128) String agentId,
        @Size(max = 128) String batchId,
        @Pattern(regexp = "macos|windows|linux") String osType,
        LocalDateTime collectedAt,
        @NotEmpty @Size(max = 100) List<@Valid HostAssetPayload> assets
) {
    public record HostAssetPayload(
            @NotBlank @Size(max = 128) String hostname,
            @NotBlank @Size(max = 64) String primaryIp,
            @Pattern(regexp = "macos|windows|linux") String osType,
            @Size(max = 128) String osVersion,
            @Size(max = 32) List<@Size(max = 64) String> ipAddresses,
            @Size(max = 32) List<@Size(max = 64) String> macAddresses,
            @Size(max = 128) String ownerName,
            @Size(max = 64) String deptName,
            @Size(max = 64) Map<@Size(max = 64) String, @Size(max = 255) String> facts,
            LocalDateTime observedAt
    ) {
    }
}
