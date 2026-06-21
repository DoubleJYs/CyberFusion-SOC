package com.zhangjiyan.template.soc.keeper;

import jakarta.validation.constraints.NotBlank;

public record SecurityKeeperCheckupRequest(@NotBlank(message = "assetIp 不能为空") String assetIp) {
}
