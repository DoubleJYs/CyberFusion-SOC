package com.zhangjiyan.template.soc.algorithm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_algorithm_evaluation")
public class SocAlgorithmEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String evaluationNo;
    private String algorithmType;
    private Long policyId;
    private Integer policyVersion;
    private String batchId;
    private LocalDateTime timeRangeStart;
    private LocalDateTime timeRangeEnd;
    private Integer inputCount;
    private Integer outputCount;
    private String diffSummaryJson;
    private String resultSummary;
    private Long createdBy;
    private LocalDateTime createdAt;
}
