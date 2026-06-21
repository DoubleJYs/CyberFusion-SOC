package com.zhangjiyan.template.soc.risk;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RiskScoringPolicyRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_\\-]{3,80}") String policyCode,
        @NotBlank @Size(max = 128) String policyName,
        @Size(max = 500) String description,
        @Pattern(regexp = "draft|active|disabled") String status,
        Boolean enabled,
        @Min(0) @Max(100) Integer criticalAssetWeight,
        @Min(0) @Max(100) Integer internetExposedWeight,
        @Min(0) @Max(100) Integer criticalAlertWeight,
        @Min(0) @Max(100) Integer highAlertWeight,
        @Min(0) @Max(100) Integer mediumAlertWeight,
        @Min(0) @Max(100) Integer criticalVulnerabilityWeight,
        @Min(0) @Max(100) Integer highVulnerabilityWeight,
        @Min(0) @Max(100) Integer baselineFailedWeight,
        @Min(0) @Max(100) Integer fimUnreviewedWeight,
        @Min(0) @Max(100) Integer externalEventWeight,
        @Min(0) @Max(100) Integer overdueTicketWeight,
        @Min(0) @Max(100) Integer openPlaybookTaskWeight,
        @Min(0) @Max(100) Integer employeePendingTaskWeight,
        @Min(0) @Max(100) Integer closedTicketReduceWeight,
        @Min(0) @Max(100) Integer completedPlaybookReduceWeight,
        @Min(1) @Max(100) Integer maxScore
) {
}
