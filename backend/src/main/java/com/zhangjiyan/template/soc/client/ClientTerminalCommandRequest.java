package com.zhangjiyan.template.soc.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientTerminalCommandRequest(
        @NotBlank @Size(max = 64) String assetIp,
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
        String commandKey,
        @Pattern(regexp = "Linux|macOS|Windows")
        String osType,
        @Size(max = 240) String note,
        Boolean linkAlert
) {
}
