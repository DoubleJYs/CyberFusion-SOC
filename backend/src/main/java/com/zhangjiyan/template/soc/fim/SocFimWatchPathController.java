package com.zhangjiyan.template.soc.fim;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/fim/watch-paths")
public class SocFimWatchPathController {

    private final FimWatchPathService service;

    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:view')")
    public ApiResult<PageResult<SocFimWatchPath>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                       @RequestParam(defaultValue = "10") long pageSize,
                                                       @RequestParam(required = false) String osType,
                                                       @RequestParam(required = false) String hostName,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.page(pageNum, pageSize, osType, hostName, status, keyword));
    }

    @PostMapping
    @OperationAudit("SOC_FIM_WATCH.CREATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:create')")
    public ApiResult<SocFimWatchPath> create(@Valid @RequestBody FimWatchPathRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationAudit("SOC_FIM_WATCH.UPDATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update')")
    public ApiResult<SocFimWatchPath> update(@PathVariable Long id, @Valid @RequestBody FimWatchPathRequest request) {
        return ApiResult.ok(service.update(id, request));
    }

    @PostMapping("/{id}/publish")
    @OperationAudit("SOC_FIM_WATCH.PUBLISH")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:publish')")
    public ApiResult<SocFimWatchPath> publish(@PathVariable Long id) {
        return ApiResult.ok(service.publish(id));
    }

    @PostMapping("/{id}/disable")
    @OperationAudit("SOC_FIM_WATCH.DISABLE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:disable')")
    public ApiResult<SocFimWatchPath> disable(@PathVariable Long id) {
        return ApiResult.ok(service.disable(id));
    }
}
