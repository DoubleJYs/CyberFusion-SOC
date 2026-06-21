package com.zhangjiyan.template.common.excel;

import java.util.List;

public final class ExcelExportUtils {

    private ExcelExportUtils() {
    }

    public static byte[] export(String sheetName, List<String> headers, List<List<String>> rows) {
        return SimpleXlsxUtils.writeWorkbook(sheetName, headers, rows);
    }
}
