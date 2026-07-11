package com.zhangjiyan.template.common.export;

import com.zhangjiyan.template.common.excel.ExcelExportUtils;
import com.zhangjiyan.template.common.excel.SimpleXlsxUtils;
import com.zhangjiyan.template.common.pdf.SimplePdfUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentExportUtilsTest {

    @Test
    void excelExportProducesReadableXlsxWorkbook() {
        byte[] bytes = ExcelExportUtils.export(
                "SOC报表",
                List.of("模块", "指标", "内容"),
                List.of(
                        List.of("基础信息", "报表编号", "RPT-DAILY-202607100001"),
                        List.of("整改建议", "建议", "优先复核高风险资产")
                )
        );

        List<List<String>> rows = SimpleXlsxUtils.readWorkbook(new ByteArrayInputStream(bytes));

        assertThat(bytes).startsWith("PK".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(rows).contains(
                List.of("模块", "指标", "内容"),
                List.of("基础信息", "报表编号", "RPT-DAILY-202607100001"),
                List.of("整改建议", "建议", "优先复核高风险资产")
        );
    }

    @Test
    void pdfExportProducesPdfDocumentBytes() {
        byte[] bytes = SimplePdfUtils.writeDocument(
                "安全运营日报",
                List.of("报表编号：RPT-DAILY-202607100001", "摘要：今日未发现高危告警")
        );

        assertThat(new String(bytes, 0, 8, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.4");
        assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).contains("%%EOF");
    }
}
