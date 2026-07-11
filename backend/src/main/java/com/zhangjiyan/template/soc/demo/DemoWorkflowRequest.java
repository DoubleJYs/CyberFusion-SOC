package com.zhangjiyan.template.soc.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record DemoWorkflowRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9_.:-]+$") String runId,
        @NotBlank @Size(max = 80) String batchId,
        @NotBlank @Size(max = 80) String selectedCaseId,
        @NotBlank @Pattern(regexp = "^(scenario|evidence|alerts|tickets|report)$") String stepKey,
        @NotBlank @Pattern(regexp = "^(active|completed)$") String status,
        @NotNull Map<String, Integer> counts,
        @NotNull List<@Valid DemoWorkflowLogRequest> logs
) {
}
