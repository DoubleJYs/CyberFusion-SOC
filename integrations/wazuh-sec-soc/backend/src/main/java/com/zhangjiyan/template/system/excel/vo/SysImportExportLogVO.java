package com.zhangjiyan.template.system.excel.vo;

import java.time.LocalDateTime;

public record SysImportExportLogVO(
        Long id,
        String taskNo,
        String taskType,
        String templateCode,
        Long fileId,
        Integer totalCount,
        Integer successCount,
        Integer failCount,
        String status,
        String errorSummary,
        Long operatorId,
        String operatorName,
        LocalDateTime createdAt
) {
}
