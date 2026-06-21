package com.zhangjiyan.template.soc.policy.adapter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EventAdapterMappingsRequest(
        @Valid List<FieldMappingRequest> fieldMappings,
        @Valid List<SeverityMappingRequest> severityMappings,
        @Valid List<AlertLinkRuleRequest> alertLinkRules
) {
    public record FieldMappingRequest(
            @NotBlank @Size(max = 160) String sourceFieldPath,
            @NotBlank @Size(max = 80) String normalizedField,
            Boolean required,
            @NotBlank @Pattern(regexp = "direct|string|number|timestamp|lowercase|uppercase|join|first_non_empty") String transformType,
            @Size(max = 255) String defaultValue,
            @Size(max = 255) String exampleValue,
            @Min(0) @Max(10000) Integer sortOrder,
            Boolean enabled
    ) {
    }

    public record SeverityMappingRequest(
            @NotBlank @Size(max = 80) String sourceValue,
            @NotBlank @Pattern(regexp = "critical|high|medium|low|info") String normalizedSeverity,
            @Min(0) @Max(100) Integer riskScore,
            Boolean enabled
    ) {
    }

    public record AlertLinkRuleRequest(
            @NotBlank @Size(max = 80) String eventType,
            @NotBlank @Pattern(regexp = "critical|high|medium|low|info") String minSeverity,
            Boolean linkAlertsDefault,
            @Size(max = 80) String alertRuleIdField,
            @Size(max = 255) String alertNameTemplate,
            @NotBlank @Size(max = 500) String dedupKeyFieldsJson,
            Boolean enabled
    ) {
    }
}
