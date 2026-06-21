package com.zhangjiyan.template.system.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class SysFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalName;
    private String storedName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String storageType;
    private String storagePath;
    private String accessUrl;
    private String md5;
    private String bizType;
    private Long uploaderId;
    private String uploaderName;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
