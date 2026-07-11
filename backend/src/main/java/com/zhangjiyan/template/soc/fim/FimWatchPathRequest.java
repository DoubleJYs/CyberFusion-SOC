package com.zhangjiyan.template.soc.fim;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FimWatchPathRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 128) String hostName,
        @NotBlank @Pattern(regexp = "macos|windows|linux") String osType,
        @NotBlank @Size(max = 500) String watchPath,
        @NotBlank @Pattern(regexp = "host_log|audit|file_integrity|application_log|custom") String purpose,
        Boolean recursive,
        @Min(1) @Max(2000) Integer maxEntries,
        @Pattern(regexp = "draft|active|disabled") String status,
        Boolean enabled
) {
}
