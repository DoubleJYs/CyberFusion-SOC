package com.zhangjiyan.template.soc.baseline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_baseline_check")
public class SocBaselineCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String checkCode;
    private String category;
    private String checkItem;
    private String assetName;
    private String assetIp;
    private String result;
    private String severity;
    private Integer passRate;
    private String remediation;
    private String status;
    private String sourceType;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime checkedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
