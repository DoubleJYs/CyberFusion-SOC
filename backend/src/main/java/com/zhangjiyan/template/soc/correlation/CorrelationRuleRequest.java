package com.zhangjiyan.template.soc.correlation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CorrelationRuleRequest(
        @NotBlank String ruleKey,
        @NotBlank String ruleName,
        @NotBlank String ruleType,
        String sourceTypesJson,
        String eventTypesJson,
        String groupByJson,
        @NotNull Integer threshold,
        @NotNull Integer timeframeSeconds,
        String sequenceJson,
        String severityFloor,
        Boolean enabled,
        String status,
        Integer version,
        String description,
        String safetyNote
) {
}
