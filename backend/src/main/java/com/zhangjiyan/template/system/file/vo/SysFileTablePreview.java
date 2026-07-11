package com.zhangjiyan.template.system.file.vo;

import java.util.List;

public record SysFileTablePreview(
        Long fileId,
        String originalName,
        String format,
        List<String> headers,
        List<List<String>> rows,
        int totalRows,
        boolean truncated
) {
}
