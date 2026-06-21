package com.zhangjiyan.template.system.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record NoticeRequest(
        @NotBlank @Size(max = 128) String noticeTitle,
        @NotBlank @Size(max = 32) String noticeType,
        @NotBlank @Size(max = 4000) String noticeContent,
        Integer pinned,
        LocalDateTime publishAt,
        LocalDateTime expireAt,
        Integer status,
        @Size(max = 255) String remark
) {
}
