package com.zhangjiyan.template.system.excel.dto;

import jakarta.validation.constraints.Size;

public record ExcelImportRequest(
        @Size(max = 128) String remark
) {
}
