package com.zhangjiyan.template.common.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties {
    private String storageType = "local";
    private String baseDir;
    private Integer maxSizeMb = 20;
    private Boolean deletePhysicalOnDelete = false;
    private List<String> allowedExtensions = new ArrayList<>(List.of(
            "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip"
    ));
}
