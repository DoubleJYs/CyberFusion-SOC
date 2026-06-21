package com.zhangjiyan.template.soc.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientSnapshotRequest(
        @NotBlank @Size(max = 64) String assetIp,
        @Pattern(regexp = "Linux|macOS|Windows")
        String osType,
        @Size(max = 240) String note,
        Boolean linkAlert
) {
}
