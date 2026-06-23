package com.zhangjiyan.template.soc.trend;

import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SOC 趋势异常", description = "基于现有事件和告警的可解释统计趋势检测")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/trends")
public class TrendAnomalyController {

    private final TrendAnomalyService service;

    @Operation(summary = "查询趋势异常 Top N")
    @GetMapping("/anomalies/top")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<List<TrendAnomalyService.TrendAnomalyItem>> topAnomalies(
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResult.ok(service.topAnomalies(limit));
    }

    @Operation(summary = "按条件查询趋势异常")
    @GetMapping("/anomalies")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view') or hasAuthority('soc:external-event:view') or hasAuthority('soc:asset:view')")
    public ApiResult<List<TrendAnomalyService.TrendAnomalyItem>> anomalies(
            @RequestParam(required = false) String assetIp,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResult.ok(service.anomalies(new TrendAnomalyService.TrendAnomalyQuery(
                assetIp, sourceType, eventType, ruleId, severity, limit)));
    }

    @Operation(summary = "查询事件趋势聚合")
    @GetMapping("/aggregations")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view') or hasAuthority('soc:external-event:view') or hasAuthority('soc:asset:view')")
    public ApiResult<List<TrendAnomalyService.TrendAggregationItem>> aggregations(
            @RequestParam(required = false) String assetIp,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "hour") String granularity,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(service.aggregations(new TrendAnomalyService.TrendAggregationQuery(
                assetIp, sourceType, eventType, ruleId, severity, granularity, limit)));
    }
}
