package com.zhangjiyan.template;

import com.zhangjiyan.template.common.result.ApiResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResultTest {

    @Test
    void okShouldUseSuccessCode() {
        ApiResult<String> result = ApiResult.ok("ready");
        assertThat(result.code()).isEqualTo("SUCCESS");
        assertThat(result.data()).isEqualTo("ready");
    }
}
