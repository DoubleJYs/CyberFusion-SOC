package com.zhangjiyan.template.soc.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_asset")
public class SocAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String hostname;
    private String ip;
    private String osType;
    private String riskLevel;
    private String sourceType;
    private Long deptId;
    private String deptName;
    private Long ownerId;
    private String ownerName;
    private Integer openAlertCount;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
