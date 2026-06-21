package com.zhangjiyan.template.soc.demo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DemoRangeBatchImportRequest(
        @Size(max = 80, message = "批次 ID 不能超过 80 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_.:-]*$", message = "批次 ID 只能包含字母、数字、点、下划线、冒号和短横线")
        String batchId,
        Boolean linkAlerts
) {
}
