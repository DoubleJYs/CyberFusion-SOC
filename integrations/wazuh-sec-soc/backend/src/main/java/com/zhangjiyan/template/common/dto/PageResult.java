package com.zhangjiyan.template.common.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public record PageResult<T>(
        List<T> records,
        long total,
        long pageNum,
        long pageSize
) {
    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
