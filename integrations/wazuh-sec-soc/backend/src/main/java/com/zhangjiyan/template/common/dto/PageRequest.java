package com.zhangjiyan.template.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRequest(
        @Min(1) long pageNum,
        @Min(1) @Max(100) long pageSize,
        String keyword,
        Integer status,
        Long deptId,
        Long postId
) {
    public PageRequest {
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
    }
}
