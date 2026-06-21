package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("soc_incident_cluster")
public class SocIncidentCluster {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clusterNo;
    private String title;
    private String summary;
    private String recommendation;
    private String severity;
    private String status;
    private Integer score;
    private String correlationKey;
    private Long assetId;
    private String assetIp;
    private String hostname;
    private String primaryAssetIp;
    private String primaryHostname;
    private String batchId;
    private String demoCaseId;
    private String sourceSummary;
    private String sourceTypes;
    private Integer evidenceCount;
    private Integer eventCount;
    private Integer alertCount;
    private Integer vulnerabilityCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long ruleId;
    private String ruleKey;
    private Long ticketId;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    @TableField(exist = false)
    private List<SocIncidentEvidence> evidence;
}
