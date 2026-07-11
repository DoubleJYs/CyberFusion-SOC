package com.zhangjiyan.template.soc.demo;

import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.audit.OperationAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "DemoWorkflow", description = "安全验证工作流与归档")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/demo-range/workflows")
public class DemoWorkflowController {

    private final DemoWorkflowService workflowService;

    @Operation(summary = "查询活动安全验证工作流")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view')")
    public ApiResult<List<DemoWorkflowResponse>> activeRuns() {
        return ApiResult.ok(workflowService.activeRuns());
    }

    @Operation(summary = "建立或更新安全验证工作流")
    @PostMapping
    @OperationAudit("SOC_DEMO_WORKFLOW.SAVE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view')")
    public ApiResult<DemoWorkflowResponse> save(@Valid @RequestBody DemoWorkflowRequest request) {
        return ApiResult.ok(workflowService.save(request));
    }

    @Operation(summary = "归档已完成安全验证工作流")
    @PostMapping("/{runId}/archive")
    @OperationAudit("SOC_DEMO_WORKFLOW.ARCHIVE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view')")
    public ApiResult<DemoWorkflowResponse> archive(@PathVariable String runId,
                                                    @Valid @RequestBody(required = false) DemoWorkflowArchiveRequest request) {
        return ApiResult.ok(workflowService.archive(runId, request == null ? new DemoWorkflowArchiveRequest(null) : request));
    }

    @Operation(summary = "删除活动安全验证工作流")
    @DeleteMapping("/{runId}")
    @OperationAudit("SOC_DEMO_WORKFLOW.DELETE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:demo-range:view')")
    public ApiResult<Void> delete(@PathVariable String runId) {
        workflowService.delete(runId);
        return ApiResult.ok();
    }
}
