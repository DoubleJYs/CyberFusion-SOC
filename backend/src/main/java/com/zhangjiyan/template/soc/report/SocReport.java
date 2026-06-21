package com.zhangjiyan.template.soc.report;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("soc_report")
public class SocReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportNo;
    private String reportType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String title;
    private String status;
    private String summary;
    private String recommendation;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
