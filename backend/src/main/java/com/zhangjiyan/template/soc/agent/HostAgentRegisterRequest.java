package com.zhangjiyan.template.soc.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record HostAgentRegisterRequest(
        @Size(max = 128) String agentId,
        @Size(max = 128) String agentName,
        @NotBlank @Size(max = 128) String hostname,
        @NotBlank @Pattern(regexp = "macos|windows|linux") String osType,
        @Size(max = 128) String osVersion,
        @Size(max = 64) String architecture,
        @NotBlank @Size(max = 64) String agentVersion,
        @Size(max = 32) List<@Size(max = 64) String> ipAddresses,
        @Size(max = 32) List<@Size(max = 64) String> macAddresses,
        @Size(max = 64) Map<@Size(max = 64) String, @Size(max = 255) String> labels
) {
}
