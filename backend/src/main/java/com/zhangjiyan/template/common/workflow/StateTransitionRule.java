package com.zhangjiyan.template.common.workflow;

public record StateTransitionRule(
        String fromStatus,
        String toStatus,
        String action,
        boolean reasonRequired,
        String description
) {
}
