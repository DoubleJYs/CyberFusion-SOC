package com.zhangjiyan.template.system.workflow.vo;

public record BizNoGenerateVO(
        String sequenceCode,
        String bizNo,
        Long currentValue
) {
}
