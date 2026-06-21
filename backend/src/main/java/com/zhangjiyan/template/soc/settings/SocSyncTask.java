package com.zhangjiyan.template.soc.settings;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_sync_task")
public class SocSyncTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String taskName;
    private String sourceType;
    private String scheduleCron;
    private Integer enabled;
    private String lastStatus;
    private LocalDateTime lastRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
