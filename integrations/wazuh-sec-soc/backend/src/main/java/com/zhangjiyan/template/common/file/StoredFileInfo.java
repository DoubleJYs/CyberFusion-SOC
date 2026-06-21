package com.zhangjiyan.template.common.file;

public record StoredFileInfo(
        String originalName,
        String storedName,
        String fileExt,
        String contentType,
        long fileSize,
        String storageType,
        String storagePath,
        String md5
) {
}
