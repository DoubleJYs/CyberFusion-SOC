package com.zhangjiyan.template.common.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFileInfo store(MultipartFile file);

    Resource loadAsResource(String storagePath);

    void delete(String storagePath);
}
