package com.zhangjiyan.template.system.excel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.excel")
public class AppExcelProperties {
    private Integer maxImportRows = 5000;
}
