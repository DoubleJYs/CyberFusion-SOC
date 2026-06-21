package com.zhangjiyan.template.system.excel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_import_export_log")
public class SysImportExportLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String taskType;
    private String templateCode;
    private Long fileId;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String status;
    private String errorSummary;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
