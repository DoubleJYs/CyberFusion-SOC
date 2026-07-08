package com.zhangjiyan.template.soc.demo;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SOC 安全验证中心", description = "离线 Demo Range 批次导入与证据闭环")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/demo-range")
public class DemoRangeController {

    private final SocOperationService service;

    @Operation(summary = "导入完整演示数据")
    @PostMapping("/demo-data/import")
    @OperationAudit("SOC_DEMO_DATA.IMPORT")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:import')")
    public ApiResult<SocOperationService.DemoDataOperationResult> importDemoData() {
        return ApiResult.ok(service.importDemoData());
    }

    @Operation(summary = "清除完整演示数据")
    @DeleteMapping("/demo-data")
    @OperationAudit("SOC_DEMO_DATA.CLEAR")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:clear')")
    public ApiResult<SocOperationService.DemoDataOperationResult> clearDemoData() {
        return ApiResult.ok(service.clearDemoData());
    }

    @Operation(summary = "清除 Host Agent 验收 fixture 数据")
    @DeleteMapping("/host-agent-smoke-data/{batchId}")
    @OperationAudit("SOC_HOST_AGENT_SMOKE_DATA.CLEAR")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:clear')")
    public ApiResult<SocOperationService.DemoDataOperationResult> clearHostAgentSmokeData(@PathVariable String batchId) {
        return ApiResult.ok(service.clearHostAgentSmokeData(batchId));
    }

    @Operation(summary = "查询演示数据状态")
    @GetMapping("/demo-data/status")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view') or hasAuthority('soc:demo-range:import') or hasAuthority('soc:demo-range:clear')")
    public ApiResult<SocOperationService.DemoDataStatus> demoDataStatus() {
        return ApiResult.ok(service.demoDataStatus());
    }

    @Operation(summary = "兼容导入完整演示数据")
    @PostMapping("/batches/import")
    @OperationAudit("SOC_DEMO_RANGE.BATCH_IMPORT")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:import') or hasAuthority('soc:external-event:import')")
    public ApiResult<SocOperationService.DemoRangeBatchImportResult> importBatch(
            @Valid @RequestBody(required = false) DemoRangeBatchImportRequest request
    ) {
        SocOperationService.DemoDataOperationResult result = service.importDemoData();
        return ApiResult.ok(new SocOperationService.DemoRangeBatchImportResult(
                result.demoRangeBatchId(),
                result.importedRangeEvents(),
                result.createdRangeAlerts(),
                result.createdRangeVulnerabilities(),
                0,
                result.errors().size(),
                result.updatedRangeEvents(),
                List.of(),
                "兼容入口会先清理旧演示数据，再导入完整演示数据；请优先使用 /demo-data/import。",
                result.errors()
        ));
    }

    @Operation(summary = "查询演示批次证据链")
    @GetMapping("/batches/{batchId}/evidence-chain")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view') or hasAuthority('soc:external-event:view')")
    public ApiResult<SocOperationService.DemoRangeEvidenceChain> evidenceChain(@PathVariable String batchId) {
        return ApiResult.ok(service.demoRangeEvidenceChain(batchId));
    }
}
