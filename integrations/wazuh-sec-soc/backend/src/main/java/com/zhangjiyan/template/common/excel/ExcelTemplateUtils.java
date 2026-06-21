package com.zhangjiyan.template.common.excel;

import java.util.List;

public final class ExcelTemplateUtils {

    private ExcelTemplateUtils() {
    }

    public static byte[] simpleTemplate(String sheetName, List<String> headers, List<List<String>> examples) {
        return SimpleXlsxUtils.writeWorkbook(sheetName, headers, examples);
    }
}
