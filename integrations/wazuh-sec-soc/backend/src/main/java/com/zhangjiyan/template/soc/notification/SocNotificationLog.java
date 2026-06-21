package com.zhangjiyan.template.soc.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_notification_log")
public class SocNotificationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long channelId;
    private String channelType;
    private String eventType;
    private String severity;
    private String bizType;
    private Long bizId;
    private String title;
    private String content;
    private String target;
    private String status;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
