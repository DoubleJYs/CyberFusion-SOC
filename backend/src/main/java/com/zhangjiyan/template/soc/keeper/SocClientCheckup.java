package com.zhangjiyan.template.soc.keeper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_client_checkup")
public class SocClientCheckup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String checkupNo;
    private Long assetId;
    private String assetIp;
    private String assetName;
    private String osType;
    private Integer score;
    private String status;
    private String summary;
    private String recommendationSummary;
    private Long ownerId;
    private Long deptId;
    private String operatorName;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
