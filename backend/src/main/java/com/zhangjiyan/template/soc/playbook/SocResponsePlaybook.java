package com.zhangjiyan.template.soc.playbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_response_playbook")
public class SocResponsePlaybook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String playbookKey;
    private String playbookName;
    private String sourceType;
    private String eventType;
    private String ruleIdPattern;
    private String minSeverity;
    private String matchExpression;
    private String description;
    private String status;
    private Integer enabled;
    private Integer version;
    private Integer sortOrder;
    private String safetyNote;
    private Long createdBy;
    private Long updatedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
