package com.zhangjiyan.template.system.log.dto;

import java.time.LocalDateTime;

public record LogResponse(
        Long id,
        String username,
        String action,
        String method,
        String path,
        String ip,
        String userAgent,
        String status,
        String message,
        LocalDateTime createdAt
) {
}
