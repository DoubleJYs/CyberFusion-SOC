package com.zhangjiyan.template.soc.risk;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_risk_scoring_policy")
public class SocRiskScoringPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String policyCode;
    private String policyName;
    private String description;
    private String status;
    private Integer enabled;
    private Integer version;
    private Integer criticalAssetWeight;
    private Integer internetExposedWeight;
    private Integer criticalAlertWeight;
    private Integer highAlertWeight;
    private Integer mediumAlertWeight;
    private Integer criticalVulnerabilityWeight;
    private Integer highVulnerabilityWeight;
    private Integer baselineFailedWeight;
    private Integer fimUnreviewedWeight;
    private Integer externalEventWeight;
    private Integer incidentOpenWeight;
    private Integer incidentHighWeight;
    private Integer overdueTicketWeight;
    private Integer openPlaybookTaskWeight;
    private Integer employeePendingTaskWeight;
    private Integer clientCheckupWarningWeight;
    private Integer clientCheckupCriticalWeight;
    private Integer closedTicketReduceWeight;
    private Integer completedPlaybookReduceWeight;
    private Integer maxScore;
    private Long createdBy;
    private Long updatedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
