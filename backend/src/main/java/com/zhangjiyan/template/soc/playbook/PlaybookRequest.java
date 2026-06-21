package com.zhangjiyan.template.soc.playbook;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PlaybookRequest(
        @NotBlank(message = "剧本编码不能为空") String playbookKey,
        @NotBlank(message = "剧本名称不能为空") String playbookName,
        @NotBlank(message = "sourceType 不能为空") String sourceType,
        String eventType,
        String ruleIdPattern,
        String minSeverity,
        String matchExpression,
        String description,
        String status,
        Boolean enabled,
        Integer sortOrder,
        String safetyNote,
        List<PlaybookStepRequest> steps
) {
}
