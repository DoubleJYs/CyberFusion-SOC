package com.zhangjiyan.template.soc.risk;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_asset_risk_snapshot")
public class SocAssetRiskSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private String assetIp;
    private String hostname;
    private Integer score;
    private String riskLevel;
    private Long policyId;
    private LocalDateTime calculatedAt;
    private String factorSummaryJson;
    private String recommendationSummary;
    private LocalDateTime createdAt;
}
