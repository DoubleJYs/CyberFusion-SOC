package com.zhangjiyan.template.soc.policy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_local_check_command")
public class SocLocalCheckCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String commandKey;
    private String displayName;
    private String osType;
    private String category;
    private String description;
    private String commandArgvJson;
    private Integer timeoutSeconds;
    private Integer outputLimitKb;
    private Integer enabled;
    private String status;
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
