package com.zhangjiyan.template.soc.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_host_agent")
public class SocHostAgent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentId;
    private String agentName;
    private String hostname;
    private String osType;
    private String osVersion;
    private String architecture;
    private String agentVersion;
    private String ipAddressesJson;
    private String macAddressesJson;
    private String labelsJson;
    private String status;
    private Integer enabled;
    private String tokenHash;
    private String lastIp;
    private Integer queueDepth;
    private Long queueBytes;
    private Long collectedCount;
    private Long sentCount;
    private Long failedCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    @TableField(exist = false)
    private Boolean runtimeControllable;
    @TableField(exist = false)
    private String runtimeControlStatus;
    @TableField(exist = false)
    private String runtimeControlReason;
}
