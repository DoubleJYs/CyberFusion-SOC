package com.zhangjiyan.template.soc.noise;

import com.zhangjiyan.template.common.audit.OperationAudit;
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

@Tag(name = "SOC 告警降噪", description = "白名单、误报管理、重复告警聚合和降噪统计")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/alert-noise")
public class SocAlertNoiseController {

    private final SocOperationService service;

    @Operation(summary = "白名单规则列表")
    @GetMapping("/whitelists")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert-noise:view')")
    public ApiResult<PageResult<SocAlertWhitelist>> whitelists(@Valid SocPageRequest request) {
        return ApiResult.ok(service.alertWhitelists(request));
    }

    @Operation(summary = "重复告警聚合")
    @GetMapping("/aggregations")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert-noise:view')")
    public ApiResult<List<SocOperationService.AlertAggregation>> aggregations(@Valid SocPageRequest request) {
        return ApiResult.ok(service.alertAggregations(request));
    }

    @Operation(summary = "告警降噪统计")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert-noise:view')")
    public ApiResult<SocOperationService.AlertNoiseSummary> summary() {
        return ApiResult.ok(service.alertNoiseSummary());
    }

    @Operation(summary = "新增白名单规则")
    @PostMapping("/whitelists")
    @OperationAudit("SOC_ALERT_NOISE.CREATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert-noise:save')")
    public ApiResult<SocAlertWhitelist> createWhitelist(@Valid @RequestBody AlertWhitelistRequest request) {
        return ApiResult.ok(service.createAlertWhitelist(request));
    }

    @Operation(summary = "编辑白名单规则")
    @PutMapping("/whitelists/{id}")
    @OperationAudit("SOC_ALERT_NOISE.UPDATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert-noise:save')")
    public ApiResult<SocAlertWhitelist> updateWhitelist(@PathVariable Long id, @Valid @RequestBody AlertWhitelistRequest request) {
        return ApiResult.ok(service.updateAlertWhitelist(id, request));
    }

    @Operation(summary = "启用或停用白名单规则")
    @PostMapping("/whitelists/{id}/status")
    @OperationAudit("SOC_ALERT_NOISE.STATUS")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert-noise:status')")
    public ApiResult<SocAlertWhitelist> updateStatus(@PathVariable Long id, @Valid @RequestBody SocStatusRequest request) {
        return ApiResult.ok(service.updateAlertWhitelistStatus(id, request));
    }
}
