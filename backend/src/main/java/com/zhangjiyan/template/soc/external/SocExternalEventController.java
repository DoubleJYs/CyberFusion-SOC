package com.zhangjiyan.template.soc.external;

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

@Tag(name = "SOC 外部事件", description = "Suricata/Zeek/MISP/OpenCTI 等外部安全数据源规范化事件")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/external-events")
public class SocExternalEventController {

    private final SocOperationService service;

    @Operation(summary = "分页查询外部事件")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:view')")
    public ApiResult<PageResult<SocExternalEvent>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.externalEvents(request));
    }

    @Operation(summary = "外部事件详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:view')")
    public ApiResult<SocExternalEvent> detail(@PathVariable Long id) {
        return ApiResult.ok(service.externalEventDetail(id));
    }

    @Operation(summary = "外部事件状态流转")
    @PostMapping("/{id}/status")
    @OperationAudit("SOC_EXTERNAL_EVENT.STATUS")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:status')")
    public ApiResult<SocExternalEvent> updateStatus(@PathVariable Long id, @Valid @RequestBody SocStatusRequest request) {
        return ApiResult.ok(service.updateExternalEventStatus(id, request));
    }

    @Operation(summary = "导入 Suricata EVE JSON")
    @PostMapping("/suricata/import")
    @OperationAudit("SOC_EXTERNAL_EVENT.SURICATA_IMPORT")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:import')")
    public ApiResult<SocOperationService.SuricataImportResult> importSuricata(@Valid @RequestBody SuricataImportRequest request) {
        return ApiResult.ok(service.importSuricataEvents(request));
    }

    @Operation(summary = "CyberFusion 多源演示导入")
    @PostMapping("/cyberfusion/import")
    @OperationAudit("CYBERFUSION.IMPORT")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:import')")
    public ApiResult<SocOperationService.CyberFusionImportResult> importCyberFusion(@Valid @RequestBody CyberFusionImportRequest request) {
        return ApiResult.ok(service.importCyberFusionEvents(request));
    }

    @Operation(summary = "CyberChef 字段分析")
    @PostMapping("/cyberchef/analyze")
    @OperationAudit("CYBERFUSION.CYBERCHEF_ANALYZE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:view')")
    public ApiResult<SocOperationService.CyberChefAnalysis> analyzeWithCyberChef(@Valid @RequestBody CyberChefAnalysisRequest request) {
        return ApiResult.ok(service.analyzeWithCyberChef(request));
    }

    @Operation(summary = "Shuffle 演示通知")
    @PostMapping("/shuffle/demo-notification")
    @OperationAudit("CYBERFUSION.SHUFFLE_DEMO")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:import')")
    public ApiResult<SocOperationService.AutomationDemoResult> shuffleDemoNotification() {
        return ApiResult.ok(service.sendShuffleDemoNotification());
    }

    @Operation(summary = "外部事件接入统计")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:view')")
    public ApiResult<List<SocOperationService.ExternalSourceSummary>> summary() {
        return ApiResult.ok(service.externalEventSummary());
    }

    @Operation(summary = "外部访问与扫描风险概览")
    @GetMapping("/risk-overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:external-event:view')")
    public ApiResult<SocOperationService.ExternalRiskOverview> riskOverview() {
        return ApiResult.ok(service.externalRiskOverview());
    }
}
