package com.zhangjiyan.template.soc.algorithm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_algorithm_evaluation_item")
public class SocAlgorithmEvaluationItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long evaluationId;
    private String itemType;
    private String itemName;
    private String previewResultJson;
    private String reason;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
