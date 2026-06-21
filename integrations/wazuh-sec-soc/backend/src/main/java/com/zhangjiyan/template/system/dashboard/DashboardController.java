package com.zhangjiyan.template.system.dashboard;

import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dashboard", description = "首页系统状态")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "首页概览")
    @GetMapping("/overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('dashboard:view')")
    public ApiResult<DashboardService.OverviewResponse> overview() {
        return ApiResult.ok(dashboardService.overview());
    }

    @Operation(summary = "最近登录")
    @GetMapping("/recent-logins")
    @PreAuthorize("hasRole('admin') or hasAuthority('dashboard:view')")
    public ApiResult<List<DashboardService.LoginItem>> recentLogins() {
        return ApiResult.ok(dashboardService.recentLogins());
    }

    @Operation(summary = "操作趋势")
    @GetMapping("/operation-trend")
    @PreAuthorize("hasRole('admin') or hasAuthority('dashboard:view')")
    public ApiResult<List<DashboardService.TrendItem>> operationTrend() {
        return ApiResult.ok(dashboardService.operationTrend());
    }

    @Operation(summary = "系统模块")
    @GetMapping("/system-modules")
    @PreAuthorize("hasRole('admin') or hasAuthority('dashboard:view')")
    public ApiResult<List<DashboardService.ModuleItem>> systemModules() {
        return ApiResult.ok(dashboardService.systemModules());
    }
}
