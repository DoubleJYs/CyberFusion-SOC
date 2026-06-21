package com.zhangjiyan.template.common.security;

import java.util.List;

public record LoginUser(Long userId, String username, String nickname, List<String> roles, List<String> permissions) {
}
