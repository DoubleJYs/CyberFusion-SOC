package com.zhangjiyan.template.soc.policy.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("soc_event_severity_mapping")
public class SocEventSeverityMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adapterId;
    private String sourceValue;
    private String normalizedSeverity;
    private Integer riskScore;
    private Integer enabled;
}
