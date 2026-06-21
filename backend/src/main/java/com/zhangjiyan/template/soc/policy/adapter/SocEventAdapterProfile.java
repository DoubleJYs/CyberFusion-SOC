package com.zhangjiyan.template.soc.policy.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_event_adapter_profile")
public class SocEventAdapterProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String displayName;
    private String description;
    private String status;
    private Integer enabled;
    private Integer version;
    private Integer sortOrder;
    private String sampleFile;
    private Long createdBy;
    private Long updatedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
