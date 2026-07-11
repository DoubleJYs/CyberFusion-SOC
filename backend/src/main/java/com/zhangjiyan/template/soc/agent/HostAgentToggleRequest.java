package com.zhangjiyan.template.soc.agent;

import jakarta.validation.constraints.NotNull;

public record HostAgentToggleRequest(
        @NotNull Boolean enabled
) {
}
