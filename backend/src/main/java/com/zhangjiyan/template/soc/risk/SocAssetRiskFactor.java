package com.zhangjiyan.template.soc.risk;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_asset_risk_factor")
public class SocAssetRiskFactor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long snapshotId;
    private Long assetId;
    private String factorType;
    private String factorName;
    private Integer factorScore;
    private Integer factorCount;
    private String relatedBizType;
    private Long relatedBizId;
    private String explanation;
    private String recommendation;
    private LocalDateTime createdAt;
}
