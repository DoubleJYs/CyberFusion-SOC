package com.zhangjiyan.template.soc.alert;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_alert")
public class SocAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertUid;
    private String sourceType;
    private Integer level;
    private String severity;
    private String ruleId;
    private String ruleDescription;
    private String assetName;
    private String assetIp;
    private String sourceIp;
    private String status;
    private String tactic;
    private String rawRef;
    private LocalDateTime eventTime;
    private Long ticketId;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    @TableField(exist = false)
    private Boolean whitelistHit;
    @TableField(exist = false)
    private String whitelistRuleName;
    @TableField(exist = false)
    private String noiseStatus;
    @TableField(exist = false)
    private Long repeatCount;
}
