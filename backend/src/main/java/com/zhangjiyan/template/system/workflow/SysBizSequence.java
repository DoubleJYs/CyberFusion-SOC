package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_biz_sequence")
public class SysBizSequence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sequenceCode;
    private String sequenceName;
    private String prefix;
    private String datePattern;
    private Long currentValue;
    private Integer step;
    private Integer length;
    private String resetPolicy;
    private LocalDate lastResetDate;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
