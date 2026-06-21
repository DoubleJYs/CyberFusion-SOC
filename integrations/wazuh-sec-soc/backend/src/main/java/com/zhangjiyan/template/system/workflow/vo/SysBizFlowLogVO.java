package com.zhangjiyan.template.system.workflow.vo;

import java.time.LocalDateTime;

public record SysBizFlowLogVO(
        Long id,
        String bizType,
        String bizId,
        String bizNo,
        String fromStatus,
        String toStatus,
        String action,
        Long operatorId,
        String operatorName,
        String reason,
        String remark,
        LocalDateTime createdAt
) {
}
