package com.zhangjiyan.template.soc.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuricataImportRequest(
        @NotBlank @Size(max = 50000) String content,
        Boolean linkAlerts
) {
}
