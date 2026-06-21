package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_correlation_rule")
public class SocCorrelationRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String ruleKey;
    private String ruleName;
    private String description;
    private Boolean enabled;
    private String status;
    private Integer version;
    private String ruleType;
    private Integer timeWindowMinutes;
    private Integer minScore;
    private Integer minCount;
    private String groupByFieldsJson;
    private String sourceTypesJson;
    private String eventTypesJson;
    private String groupByJson;
    private Integer threshold;
    private Integer timeframeSeconds;
    private String sequenceJson;
    private String severityMin;
    private String severityFloor;
    private String weightsJson;
    private String safetyNote;
    private Long createdBy;
    private Long updatedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
