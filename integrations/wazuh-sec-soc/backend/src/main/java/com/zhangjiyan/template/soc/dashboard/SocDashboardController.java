package com.zhangjiyan.template.soc.dashboard;

import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SOC 安全总览", description = "告警趋势、等级分布、资产风险排行")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/dashboard")
public class SocDashboardController {

    private final SocOperationService service;

    @Operation(summary = "安全总览指标")
    @GetMapping("/overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationService.DashboardOverview> overview() {
        return ApiResult.ok(service.dashboardOverview());
    }

    @Operation(summary = "七日告警趋势")
    @GetMapping("/alert-trend")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<List<SocOperationService.TrendItem>> trend() {
        return ApiResult.ok(service.alertTrend());
    }

    @Operation(summary = "告警等级分布")
    @GetMapping("/severity-distribution")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<List<SocOperationService.NameValue>> severityDistribution() {
        return ApiResult.ok(service.severityDistribution());
    }

    @Operation(summary = "受影响资产排行")
    @GetMapping("/affected-assets")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<List<SocOperationService.NameValue>> affectedAssets() {
        return ApiResult.ok(service.affectedAssets());
    }

    @Operation(summary = "风险评分与运营分析")
    @GetMapping("/risk-analytics")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<SocOperationService.RiskAnalytics> riskAnalytics() {
        return ApiResult.ok(service.riskAnalytics());
    }
}
