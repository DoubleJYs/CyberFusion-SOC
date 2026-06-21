package com.zhangjiyan.template.soc.fim;

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

@Tag(name = "SOC 文件完整性", description = "文件新增、修改、删除和权限变化")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/fim")
public class SocFileIntegrityController {

    private final SocOperationService service;

    @Operation(summary = "分页查询文件完整性事件")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:fim:view')")
    public ApiResult<PageResult<SocFileIntegrityEvent>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.fileIntegrityEvents(request));
    }

    @Operation(summary = "文件完整性事件详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:fim:view')")
    public ApiResult<SocFileIntegrityEvent> detail(@PathVariable Long id) {
        return ApiResult.ok(service.fileIntegrityDetail(id));
    }

    @Operation(summary = "文件完整性事件状态流转")
    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:fim:status')")
    public ApiResult<SocFileIntegrityEvent> updateStatus(@PathVariable Long id, @Valid @RequestBody SocStatusRequest request) {
        return ApiResult.ok(service.updateFileIntegrityStatus(id, request));
    }

    @Operation(summary = "文件完整性统计")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:fim:view')")
    public ApiResult<List<SocOperationService.NameValue>> summary() {
        return ApiResult.ok(service.fileIntegritySummary());
    }
}
