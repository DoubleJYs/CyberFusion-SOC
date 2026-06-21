package com.zhangjiyan.template.common.workflow;

public record StateTransitionResult(
        boolean allowed,
        String fromStatus,
        String toStatus,
        String action,
        String message
) {
}
