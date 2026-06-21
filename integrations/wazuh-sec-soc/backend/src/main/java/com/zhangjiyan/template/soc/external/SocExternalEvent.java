package com.zhangjiyan.template.soc.external;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_external_event")
public class SocExternalEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventUid;
    private String sourceType;
    private String eventType;
    private String severity;
    private String ruleId;
    private String ruleName;
    private String srcIp;
    private String destIp;
    private String assetName;
    private String assetIp;
    private String ioc;
    private String rawEvent;
    private String normalizedEvent;
    private Long alertId;
    private String status;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime eventTime;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
