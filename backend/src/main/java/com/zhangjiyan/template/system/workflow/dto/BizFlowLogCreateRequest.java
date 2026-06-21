package com.zhangjiyan.template.system.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BizFlowLogCreateRequest(
        @NotBlank @Size(max = 64) String bizType,
        @NotBlank @Size(max = 64) String bizId,
        @Size(max = 64) String bizNo,
        @Size(max = 64) String fromStatus,
        @Size(max = 64) String toStatus,
        @NotBlank @Size(max = 64) String action,
        Long operatorId,
        @Size(max = 64) String operatorName,
        @Size(max = 255) String reason,
        @Size(max = 500) String remark
) {
}
