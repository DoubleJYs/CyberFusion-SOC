package com.zhangjiyan.template.soc.fim;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A least-privilege directory authorization consumed by a Host Agent.
 * File contents are never part of this policy or the FIM ingest contract.
 */
@Data
@TableName("soc_fim_watch_path")
public class SocFimWatchPath {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String displayName;
    private String hostName;
    private String osType;
    private String watchPath;
    private String purpose;
    @TableField("is_recursive")
    private Integer recursive;
    private Integer maxEntries;
    private String status;
    private Integer enabled;
    private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
