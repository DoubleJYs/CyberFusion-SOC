package com.zhangjiyan.template.soc.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_ticket")
public class SocTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private Long alertId;
    private String title;
    private String severity;
    private String status;
    private Long assigneeId;
    private String assigneeName;
    private Long reviewerId;
    private String reviewConclusion;
    private String resolution;
    private Long deptId;
    private LocalDateTime dueAt;
    private LocalDateTime closedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
