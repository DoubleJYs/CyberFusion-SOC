package com.zhangjiyan.template.soc.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_ingest_batch")
public class SocIngestBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String agentId;
    private Long agentDbId;
    private String sourceOs;
    private String ingestType;
    private Integer itemCount;
    private Integer acceptedCount;
    private Integer duplicateCount;
    private Integer rejectedCount;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
