package com.zhangjiyan.template.soc.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientLabEventRequest(
        @NotBlank @Size(max = 64) String assetIp,
        @NotBlank
        @Pattern(regexp = "login_failure|sensitive_path|upload_probe|privilege_boundary|data_query|persistence_signal")
        String actionType,
        @Size(max = 80) String targetName,
        @Size(max = 32) String targetType,
        @Size(max = 160) String targetAddress,
        @Size(max = 160) String targetScope,
        @Size(max = 80) String sessionId,
        @Size(max = 80) String sessionName,
        @Size(max = 32) String sessionPhase,
        @Size(max = 240) String operatorNote,
        @Size(max = 240) String note,
        Boolean linkAlert
) {
}
