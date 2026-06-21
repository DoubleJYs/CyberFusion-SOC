package com.zhangjiyan.template.soc.keeper;

import jakarta.validation.constraints.Size;

public record SecurityKeeperRecommendationRequest(
        @Size(max = 500, message = "说明不能超过 500 个字符") String note
) {
}
