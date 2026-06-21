package com.zhangjiyan.template.system.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {
}
