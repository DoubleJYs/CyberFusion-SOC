package com.zhangjiyan.template.common.file;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FileValidationProperties {

    private final FileStorageProperties properties;

    public long maxSizeBytes() {
        return properties.getMaxSizeMb() * 1024L * 1024L;
    }

    public List<String> allowedExtensions() {
        return properties.getAllowedExtensions();
    }
}
