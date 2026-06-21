package com.zhangjiyan.template.soc.baseline;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.dto.SocStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SOC 基线核查", description = "SSH、密码策略、防火墙、系统服务和敏感文件权限核查")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/baselines")
public class SocBaselineController {

    private final SocOperationService service;

    @Operation(summary = "分页查询基线核查项")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:baseline:view')")
    public ApiResult<PageResult<SocBaselineCheck>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.baselines(request));
    }

    @Operation(summary = "基线核查详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:baseline:view')")
    public ApiResult<SocBaselineCheck> detail(@PathVariable Long id) {
        return ApiResult.ok(service.baselineDetail(id));
    }

    @Operation(summary = "基线状态流转")
    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:baseline:status')")
    public ApiResult<SocBaselineCheck> updateStatus(@PathVariable Long id, @Valid @RequestBody SocStatusRequest request) {
        return ApiResult.ok(service.updateBaselineStatus(id, request));
    }

    @Operation(summary = "基线统计")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:baseline:view')")
    public ApiResult<List<SocOperationService.NameValue>> summary() {
        return ApiResult.ok(service.baselineSummary());
    }
}
