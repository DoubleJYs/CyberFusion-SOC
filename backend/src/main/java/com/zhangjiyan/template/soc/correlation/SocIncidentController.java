package com.zhangjiyan.template.soc.correlation;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SOC 安全事件簇", description = "多源事件关联、事件簇详情、转工单和关闭")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/incidents")
public class SocIncidentController {

    private final CorrelationService service;

    @Operation(summary = "分页查询安全事件簇")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:list')")
    public ApiResult<PageResult<SocIncidentCluster>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String severity,
                                                          @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.incidents(pageNum, pageSize, status, severity, keyword));
    }

    @Operation(summary = "安全事件簇详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:view') or hasAuthority('soc:incident:list')")
    public ApiResult<SocIncidentCluster> detail(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id));
    }

    @Operation(summary = "执行事件关联")
    @PostMapping("/correlate")
    @OperationAudit("SOC_INCIDENT.CORRELATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:correlate')")
    public ApiResult<CorrelationService.CorrelateResult> correlate() {
        return ApiResult.ok(service.correlate());
    }

    @Operation(summary = "事件簇转工单")
    @PostMapping("/{id}/ticket")
    @OperationAudit("SOC_INCIDENT.TICKET")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:ticket')")
    public ApiResult<SocTicket> createTicket(@PathVariable Long id, @RequestBody(required = false) IncidentActionRequest request) {
        return ApiResult.ok(service.createTicket(id, request));
    }

    @Operation(summary = "关闭事件簇")
    @PostMapping("/{id}/close")
    @OperationAudit("SOC_INCIDENT.CLOSE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:close')")
    public ApiResult<SocIncidentCluster> close(@PathVariable Long id, @RequestBody(required = false) IncidentActionRequest request) {
        return ApiResult.ok(service.close(id, request));
    }

    @Operation(summary = "告警关联事件簇")
    @GetMapping("/by-alert/{alertId}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:list') or hasAuthority('soc:alert:view')")
    public ApiResult<List<SocIncidentCluster>> byAlert(@PathVariable Long alertId) {
        return ApiResult.ok(service.relatedIncidentsForAlert(alertId));
    }

    @Operation(summary = "资产关联事件簇")
    @GetMapping("/by-asset/{assetId}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:list') or hasAuthority('soc:asset:view')")
    public ApiResult<List<SocIncidentCluster>> byAsset(@PathVariable Long assetId) {
        return ApiResult.ok(service.incidentsForAsset(assetId));
    }
}
