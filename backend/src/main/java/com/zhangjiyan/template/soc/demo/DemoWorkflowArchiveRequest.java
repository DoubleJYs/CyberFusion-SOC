package com.zhangjiyan.template.soc.demo;

import jakarta.validation.constraints.Size;

public record DemoWorkflowArchiveRequest(@Size(max = 255) String reason) {
}
