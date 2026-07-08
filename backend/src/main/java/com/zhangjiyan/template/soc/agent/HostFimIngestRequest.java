package com.zhangjiyan.template.soc.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record HostFimIngestRequest(
        @NotBlank @Size(max = 128) String agentId,
        @Size(max = 128) String batchId,
        @Pattern(regexp = "macos|windows|linux") String osType,
        LocalDateTime collectedAt,
        @NotEmpty @Size(max = 500) List<@Valid HostFimPayload> events
) {
    public record HostFimPayload(
            @NotBlank @Size(max = 128) String eventUid,
            @NotBlank @Pattern(regexp = "created|modified|deleted|permission|owner|hash") String action,
            @NotBlank @Pattern(regexp = "critical|high|medium|low|info") String severity,
            @NotBlank @Size(max = 128) String hostname,
            @NotBlank @Size(max = 64) String assetIp,
            @NotBlank @Size(max = 500) String filePath,
            @Size(max = 255) String ruleName,
            @Size(max = 128) String beforeHash,
            @Size(max = 128) String afterHash,
            LocalDateTime eventTime,
            Map<String, Object> attributes
    ) {
    }
}
