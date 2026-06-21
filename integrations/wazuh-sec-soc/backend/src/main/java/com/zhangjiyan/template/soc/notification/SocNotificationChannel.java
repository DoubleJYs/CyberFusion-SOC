package com.zhangjiyan.template.soc.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_notification_channel")
public class SocNotificationChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channelName;
    private String channelType;
    private String target;
    private Integer enabled;
    private String minSeverity;
    private String triggerEvent;
    private String sendMode;
    private String lastStatus;
    private LocalDateTime lastSentAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
