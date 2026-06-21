package com.zhangjiyan.template.soc.policy.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("soc_event_field_mapping")
public class SocEventFieldMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adapterId;
    private String sourceFieldPath;
    private String normalizedField;
    private Integer required;
    private String transformType;
    private String defaultValue;
    private String exampleValue;
    private Integer sortOrder;
    private Integer enabled;
}
