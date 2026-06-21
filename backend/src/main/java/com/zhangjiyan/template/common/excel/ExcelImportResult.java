package com.zhangjiyan.template.common.excel;

import java.util.List;

public record ExcelImportResult(
        int totalCount,
        int successCount,
        int failCount,
        List<ExcelImportError> errors
) {
    public static ExcelImportResult of(int totalCount, List<ExcelImportError> errors) {
        int failCount = errors == null ? 0 : errors.size();
        return new ExcelImportResult(totalCount, Math.max(totalCount - failCount, 0), failCount, errors == null ? List.of() : errors);
    }
}
