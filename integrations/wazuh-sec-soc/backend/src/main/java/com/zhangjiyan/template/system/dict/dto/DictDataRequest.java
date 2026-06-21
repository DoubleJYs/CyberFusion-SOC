package com.zhangjiyan.template.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DictDataRequest(@NotNull Long dictTypeId, @NotBlank String dictLabel, @NotBlank String dictValue, Integer sortOrder, Integer status) {
}
