package com.zhangjiyan.template.common.workflow;

public record StateTransition(
        String fromStatus,
        String toStatus,
        String action
) {
}
