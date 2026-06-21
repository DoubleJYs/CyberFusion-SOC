package com.zhangjiyan.template.soc.alert;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SOC 告警中心", description = "告警查询、确认、误报、忽略、关闭、转工单")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/alerts")
public class SocAlertController {

    private final SocOperationService service;

    @Operation(summary = "分页查询告警")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:view')")
    public ApiResult<PageResult<SocAlert>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.alerts(request));
    }

    @Operation(summary = "告警详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:view')")
    public ApiResult<SocAlert> detail(@PathVariable Long id) {
        return ApiResult.ok(service.alertDetail(id));
    }

    @Operation(summary = "确认告警")
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:ack')")
    public ApiResult<SocAlert> acknowledge(@PathVariable Long id, @Valid @RequestBody AlertActionRequest request) {
        return ApiResult.ok(service.updateAlertStatus(id, "acknowledged", request));
    }

    @Operation(summary = "标记误报")
    @PostMapping("/{id}/false-positive")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:false-positive')")
    public ApiResult<SocAlert> falsePositive(@PathVariable Long id, @Valid @RequestBody AlertActionRequest request) {
        return ApiResult.ok(service.updateAlertStatus(id, "false_positive", request));
    }

    @Operation(summary = "忽略告警")
    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:ignore')")
    public ApiResult<SocAlert> ignore(@PathVariable Long id, @Valid @RequestBody AlertActionRequest request) {
        return ApiResult.ok(service.updateAlertStatus(id, "ignored", request));
    }

    @Operation(summary = "关闭告警")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:close')")
    public ApiResult<SocAlert> close(@PathVariable Long id, @Valid @RequestBody AlertActionRequest request) {
        return ApiResult.ok(service.updateAlertStatus(id, "closed", request));
    }

    @Operation(summary = "转为工单")
    @PostMapping("/{id}/ticket")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:alert:ticket')")
    public ApiResult<SocTicket> createTicket(@PathVariable Long id, @Valid @RequestBody AlertActionRequest request) {
        return ApiResult.ok(service.createTicket(id, request));
    }
}
