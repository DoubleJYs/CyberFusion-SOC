package com.zhangjiyan.template.common.excel;

public record ExcelImportError(
        int rowNumber,
        String fieldName,
        String reason
) {
}
