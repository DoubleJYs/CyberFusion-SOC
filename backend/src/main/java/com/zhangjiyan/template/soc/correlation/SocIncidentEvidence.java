package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_incident_evidence")
public class SocIncidentEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clusterId;
    private String evidenceType;
    private Long evidenceId;
    private String evidenceUid;
    private String sourceType;
    private String eventType;
    private String severity;
    private String ruleId;
    private String assetIp;
    private String hostname;
    private String targetUrl;
    private String batchId;
    private String demoCaseId;
    private LocalDateTime eventTime;
    private Integer relationScore;
    private String relationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
