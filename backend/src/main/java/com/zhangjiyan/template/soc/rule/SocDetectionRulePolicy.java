package com.zhangjiyan.template.soc.rule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_detection_rule_policy")
public class SocDetectionRulePolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String ruleId;
    private String ruleName;
    private String detectionCategory;
    private String severity;
    private String detectionSummary;
    private String status;
    private Integer enabled;
    private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
