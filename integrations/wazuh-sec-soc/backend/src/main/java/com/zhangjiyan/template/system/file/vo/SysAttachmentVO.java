package com.zhangjiyan.template.system.file.vo;

import java.time.LocalDateTime;

public record SysAttachmentVO(
        Long id,
        String bizType,
        String bizId,
        Long fileId,
        String fileName,
        String contentType,
        Long fileSize,
        String uploaderName,
        String downloadUrl,
        String previewUrl,
        Integer sortOrder,
        String remark,
        LocalDateTime createdAt
) {
}
