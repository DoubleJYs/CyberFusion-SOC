package com.zhangjiyan.template.system.file.vo;

import java.time.LocalDateTime;

public record SysFileVO(
        Long id,
        String originalName,
        String fileExt,
        String contentType,
        Long fileSize,
        String storageType,
        String accessUrl,
        String md5,
        String bizType,
        Long uploaderId,
        String uploaderName,
        String downloadUrl,
        String previewUrl,
        LocalDateTime createdAt
) {
}
