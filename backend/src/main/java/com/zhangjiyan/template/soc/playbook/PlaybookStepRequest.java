package com.zhangjiyan.template.soc.playbook;

import jakarta.validation.constraints.NotBlank;

public record PlaybookStepRequest(
        Long id,
        @NotBlank(message = "步骤编码不能为空") String stepKey,
        @NotBlank(message = "步骤名称不能为空") String stepName,
        @NotBlank(message = "步骤类型不能为空") String stepType,
        String ownerRole,
        @NotBlank(message = "步骤说明不能为空") String instruction,
        String expectedEvidence,
        Boolean requiresEmployee,
        Integer sortOrder,
        Boolean enabled
) {
}
