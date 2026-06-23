package com.zhangjiyan.template.soc.operations;

import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SOC 运营指标", description = "事件簇、风险评分、推荐动作、工单 SLA 和员工待办的可解释运营指标")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/operations")
public class SocOperationsController {

    private final SocOperationsService service;

    @Operation(summary = "运营指标总览")
    @GetMapping("/overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationsService.OperationsOverview> overview() {
        return ApiResult.ok(service.overview());
    }

    @Operation(summary = "SLA 与工单效率")
    @GetMapping("/sla")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationsService.TicketSlaMetrics> sla() {
        return ApiResult.ok(service.slaMetrics());
    }

    @Operation(summary = "风险变化趋势")
    @GetMapping("/risk-trend")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationsService.RiskTrendMetrics> riskTrend() {
        return ApiResult.ok(service.riskTrendMetrics());
    }

    @Operation(summary = "推荐动作采纳")
    @GetMapping("/recommendation-adoption")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationsService.RecommendationAdoptionMetrics> recommendationAdoption() {
        return ApiResult.ok(service.recommendationAdoptionMetrics());
    }

    @Operation(summary = "员工待办与安全管家覆盖")
    @GetMapping("/client-tasks")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationsService.ClientTaskMetrics> clientTasks() {
        return ApiResult.ok(service.clientTaskMetrics());
    }
}
