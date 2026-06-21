package com.zhangjiyan.template.system.workflow;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.system.workflow.dto.BizFlowLogCreateRequest;
import com.zhangjiyan.template.system.workflow.vo.SysBizFlowLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "BizFlowLog", description = "业务流程日志")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/biz-flow-logs")
public class SysBizFlowLogController {

    private final SysBizFlowLogService flowLogService;

    @Operation(summary = "业务流程日志分页")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:flowlog:list')")
    public ApiResult<PageResult<SysBizFlowLogVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                       @RequestParam(defaultValue = "10") long pageSize,
                                                       @RequestParam(required = false) String bizType,
                                                       @RequestParam(required = false) String bizId,
                                                       @RequestParam(required = false) String bizNo,
                                                       @RequestParam(required = false) String operatorName,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResult.ok(flowLogService.pageLogs(pageNum, pageSize, bizType, bizId, bizNo, operatorName, startTime, endTime));
    }

    @Operation(summary = "业务流程日志详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:flowlog:list')")
    public ApiResult<SysBizFlowLogVO> detail(@PathVariable Long id) {
        return ApiResult.ok(flowLogService.detail(id));
    }

    @Operation(summary = "写入业务流程日志")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:flowlog:create')")
    public ApiResult<SysBizFlowLogVO> create(@Valid @RequestBody BizFlowLogCreateRequest request) {
        return ApiResult.ok(flowLogService.create(request));
    }
}
