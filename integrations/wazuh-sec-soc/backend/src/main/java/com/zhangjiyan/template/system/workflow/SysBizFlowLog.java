package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_biz_flow_log")
public class SysBizFlowLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizId;
    private String bizNo;
    private String fromStatus;
    private String toStatus;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String reason;
    private String remark;
    private LocalDateTime createdAt;
}
