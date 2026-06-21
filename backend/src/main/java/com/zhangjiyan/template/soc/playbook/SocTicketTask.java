package com.zhangjiyan.template.soc.playbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_ticket_task")
public class SocTicketTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private Long alertId;
    private Long playbookId;
    private Long playbookStepId;
    private String taskKey;
    private String taskName;
    private String taskType;
    private String assigneeType;
    private Long assigneeId;
    private String assigneeName;
    private String instruction;
    private String expectedEvidence;
    private String evidenceText;
    private String status;
    private Integer sortOrder;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime skippedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
