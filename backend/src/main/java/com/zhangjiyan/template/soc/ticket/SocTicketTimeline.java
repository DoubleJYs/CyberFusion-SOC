package com.zhangjiyan.template.soc.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_ticket_timeline")
public class SocTicketTimeline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
