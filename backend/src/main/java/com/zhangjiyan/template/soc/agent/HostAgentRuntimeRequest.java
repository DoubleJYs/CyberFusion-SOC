package com.zhangjiyan.template.soc.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HostAgentRuntimeRequest(
        @NotBlank @Pattern(regexp = "start|stop") String action
) {
}
