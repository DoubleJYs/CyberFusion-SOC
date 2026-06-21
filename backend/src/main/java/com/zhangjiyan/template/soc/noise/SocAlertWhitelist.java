package com.zhangjiyan.template.soc.noise;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_alert_whitelist")
public class SocAlertWhitelist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String ruleId;
    private String assetIp;
    private String sourceIp;
    private String severity;
    private String reason;
    private Integer enabled;
    private Integer matchCount;
    private LocalDateTime lastMatchedAt;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
