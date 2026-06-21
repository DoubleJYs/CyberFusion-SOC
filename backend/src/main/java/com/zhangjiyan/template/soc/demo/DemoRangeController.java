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

@Tag(name = "SOC 安全验证中心", description = "离线 Demo Range 批次导入与证据闭环")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/demo-range")
public class DemoRangeController {

    private final SocOperationService service;

    @Operation(summary = "一键导入离线演示批次")
    @PostMapping("/batches/import")
    @OperationAudit("SOC_DEMO_RANGE.BATCH_IMPORT")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:import') or hasAuthority('soc:external-event:import')")
    public ApiResult<SocOperationService.DemoRangeBatchImportResult> importBatch(
            @Valid @RequestBody(required = false) DemoRangeBatchImportRequest request
    ) {
        return ApiResult.ok(service.importDemoRangeBatch(request));
    }

    @Operation(summary = "查询演示批次证据链")
    @GetMapping("/batches/{batchId}/evidence-chain")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view') or hasAuthority('soc:external-event:view')")
    public ApiResult<SocOperationService.DemoRangeEvidenceChain> evidenceChain(@PathVariable String batchId) {
        return ApiResult.ok(service.demoRangeEvidenceChain(batchId));
    }
}
