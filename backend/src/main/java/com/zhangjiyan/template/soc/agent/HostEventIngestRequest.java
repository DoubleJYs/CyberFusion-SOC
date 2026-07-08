package com.zhangjiyan.template.soc.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record HostEventIngestRequest(
        @NotBlank @Size(max = 128) String agentId,
        @Size(max = 128) String batchId,
        @Pattern(regexp = "macos|windows|linux") String osType,
        LocalDateTime collectedAt,
        @NotEmpty @Size(max = 500) List<@Valid HostEventPayload> events
) {
    public record HostEventPayload(
            @NotBlank @Size(max = 128) String eventUid,
            @Size(max = 64) String sourceModule,
            @NotBlank @Size(max = 64) String eventType,
            @NotBlank @Pattern(regexp = "critical|high|medium|low|info") String severity,
            @Size(max = 64) String ruleId,
            @Size(max = 255) String ruleName,
            @Size(max = 64) String srcIp,
            @Size(max = 64) String destIp,
            @Size(max = 128) String assetName,
            @Size(max = 64) String assetIp,
            @Size(max = 500) String targetUrl,
            @Size(max = 64) String action,
            @Size(max = 255) String ioc,
            LocalDateTime eventTime,
            Map<String, Object> raw,
            Map<String, Object> normalized
    ) {
    }
}
