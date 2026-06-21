package com.zhangjiyan.template.system.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_attachment")
public class SysAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizId;
    private Long fileId;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
