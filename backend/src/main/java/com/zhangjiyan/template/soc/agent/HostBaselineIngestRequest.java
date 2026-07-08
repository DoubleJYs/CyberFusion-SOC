package com.zhangjiyan.template.soc.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record HostBaselineIngestRequest(
        @NotBlank @Size(max = 128) String agentId,
        @Size(max = 128) String batchId,
        @Pattern(regexp = "macos|windows|linux") String osType,
        LocalDateTime collectedAt,
        @NotEmpty @Size(max = 300) List<@Valid HostBaselinePayload> checks
) {
    public record HostBaselinePayload(
            @NotBlank @Size(max = 64) String checkCode,
            @NotBlank @Size(max = 64) String category,
            @NotBlank @Size(max = 255) String checkItem,
            @NotBlank @Size(max = 128) String assetName,
            @NotBlank @Size(max = 64) String assetIp,
            @NotBlank @Pattern(regexp = "pass|failed|warning|unknown") String result,
            @NotBlank @Pattern(regexp = "critical|high|medium|low|info") String severity,
            @Min(0) @Max(100) Integer passRate,
            @Size(max = 1000) String remediation,
            @Pattern(regexp = "open|failed|passed|reviewing|fixed|ignored") String status,
            LocalDateTime checkedAt,
            Map<String, Object> evidence
    ) {
    }
}
