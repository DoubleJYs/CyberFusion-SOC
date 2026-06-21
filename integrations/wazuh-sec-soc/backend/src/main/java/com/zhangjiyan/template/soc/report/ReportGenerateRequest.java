package com.zhangjiyan.template.soc.report;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ReportGenerateRequest(
        @NotBlank(message = "报表类型不能为空") String reportType,
        LocalDate periodStart,
        LocalDate periodEnd
) {
}
