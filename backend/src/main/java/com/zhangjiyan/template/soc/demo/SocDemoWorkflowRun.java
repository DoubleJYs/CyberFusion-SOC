package com.zhangjiyan.template.soc.demo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_demo_workflow_run")
public class SocDemoWorkflowRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String batchId;
    private String selectedCaseId;
    private String stepKey;
    private String status;
    private String countsJson;
    private String logsJson;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastVisitedAt;
}
