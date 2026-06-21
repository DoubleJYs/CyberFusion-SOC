package com.zhangjiyan.template.common.workflow;

public record BizNoRule(
        String prefix,
        String datePattern,
        long currentValue,
        int step,
        int length,
        SequenceResetPolicy resetPolicy
) {
}
