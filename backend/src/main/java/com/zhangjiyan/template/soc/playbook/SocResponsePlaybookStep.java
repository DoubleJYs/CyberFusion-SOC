package com.zhangjiyan.template.soc.playbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_response_playbook_step")
public class SocResponsePlaybookStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playbookId;
    private String stepKey;
    private String stepName;
    private String stepType;
    private String ownerRole;
    private String instruction;
    private String expectedEvidence;
    private Integer requiresEmployee;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
