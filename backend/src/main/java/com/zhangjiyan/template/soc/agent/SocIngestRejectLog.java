package com.zhangjiyan.template.soc.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_ingest_reject_log")
public class SocIngestRejectLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String agentId;
    private String eventUid;
    private String ingestType;
    private String reasonCode;
    private String reason;
    private String payloadJson;
    private LocalDateTime createdAt;
}
