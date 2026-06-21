package com.zhangjiyan.template.soc.ticket;

import jakarta.validation.constraints.NotBlank;

public record TicketTransitionRequest(
        @NotBlank(message = "目标状态不能为空") String targetStatus,
        String remark,
        Long assigneeId,
        String assigneeName,
        Long reviewerId,
        String reviewConclusion,
        String resolution
) {
}
