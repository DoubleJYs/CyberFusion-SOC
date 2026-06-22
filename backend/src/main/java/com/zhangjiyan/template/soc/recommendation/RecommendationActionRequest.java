package com.zhangjiyan.template.soc.recommendation;

import jakarta.validation.constraints.Size;

public record RecommendationActionRequest(
        String actionType,
        String relatedBizType,
        Long relatedBizId,
        String assetIp,
        String assetName,
        @Size(max = 500, message = "说明最多 500 字") String note
) {
}
