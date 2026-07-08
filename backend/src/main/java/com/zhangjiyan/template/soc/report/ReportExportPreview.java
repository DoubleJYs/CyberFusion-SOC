package com.zhangjiyan.template.soc.report;

import java.util.List;

public record ReportExportPreview(
        Long reportId,
        String reportNo,
        String title,
        String format,
        String filename,
        String mimeType,
        List<String> headers,
        List<List<String>> rows,
        List<String> lines
) {
}
