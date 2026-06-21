package com.zhangjiyan.template.system.dict.dto;

import jakarta.validation.constraints.NotBlank;

public record DictTypeRequest(@NotBlank String dictName, @NotBlank String dictCode, Integer status) {
}
