package com.zhangjiyan.template.soc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SocPageRequest(
        @Min(1) long pageNum,
        @Min(1) @Max(100) long pageSize,
        String keyword,
        String severity,
        String status,
        String riskLevel,
        String reportType,
        String category,
        String result,
        String action,
        String sourceType,
        String eventType,
        Long deptId,
        Long assigneeId
) {
    public SocPageRequest {
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
    }
}
