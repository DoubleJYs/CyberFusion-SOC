package com.zhangjiyan.template.soc.client;

import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SOC 员工终端安全态势", description = "SOC 后台查看员工安全管家覆盖、风险、待办和本机检查复核状态")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/client-security")
public class SocClientSecurityPostureController {

    private final SocClientSecurityPostureService service;

    @Operation(summary = "员工终端安全态势总览")
    @GetMapping("/overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:client-security:view') or hasAuthority('soc:asset:view')")
    public ApiResult<SocClientSecurityPostureService.ClientSecurityPosture> overview() {
        return ApiResult.ok(service.overview());
    }
}
