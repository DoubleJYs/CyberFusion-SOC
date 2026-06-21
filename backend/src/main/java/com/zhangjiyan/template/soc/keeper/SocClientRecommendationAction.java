package com.zhangjiyan.template.soc.keeper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_client_recommendation_action")
public class SocClientRecommendationAction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recommendationKey;
    private String actionType;
    private String note;
    private String relatedType;
    private Long relatedId;
    private String assetIp;
    private String assetName;
    private Long ownerId;
    private Long deptId;
    private String operatorName;
    private LocalDateTime createdAt;
    private Integer deleted;
}
