package com.zhangjiyan.template.soc.fim;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_file_integrity_event")
public class SocFileIntegrityEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventUid;
    private String action;
    private String severity;
    private String hostname;
    private String assetIp;
    private String filePath;
    private String ruleName;
    private String status;
    private String sourceType;
    private Long ownerId;
    private Long deptId;
    private LocalDateTime eventTime;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
