package com.zhangjiyan.template.soc.playbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_playbook_match_log")
public class SocPlaybookMatchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertId;
    private Long playbookId;
    private Long ticketId;
    private String matchReason;
    private String applyStatus;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
    private Integer deleted;
}
