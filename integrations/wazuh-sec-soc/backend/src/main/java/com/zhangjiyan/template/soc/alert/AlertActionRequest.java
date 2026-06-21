package com.zhangjiyan.template.soc.alert;

import jakarta.validation.constraints.NotBlank;

public record AlertActionRequest(
        @NotBlank(message = "处置说明不能为空") String remark,
        Long assigneeId
) {
}
