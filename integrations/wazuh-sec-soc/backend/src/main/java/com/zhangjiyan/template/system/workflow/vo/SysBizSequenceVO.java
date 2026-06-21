package com.zhangjiyan.template.system.workflow.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SysBizSequenceVO(
        Long id,
        String sequenceCode,
        String sequenceName,
        String prefix,
        String datePattern,
        Long currentValue,
        Integer step,
        Integer length,
        String resetPolicy,
        LocalDate lastResetDate,
        Integer enabled,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
