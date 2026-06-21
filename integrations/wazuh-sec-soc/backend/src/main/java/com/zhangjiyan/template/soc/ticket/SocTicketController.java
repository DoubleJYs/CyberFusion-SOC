package com.zhangjiyan.template.soc.ticket;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SOC 工单中心", description = "安全事件工单状态流转与时间线")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/tickets")
public class SocTicketController {

    private final SocOperationService service;

    @Operation(summary = "分页查询工单")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:view')")
    public ApiResult<PageResult<SocTicket>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.tickets(request));
    }

    @Operation(summary = "工单详情与时间线")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:view')")
    public ApiResult<SocOperationService.TicketDetail> detail(@PathVariable Long id) {
        return ApiResult.ok(service.ticketDetail(id));
    }

    @Operation(summary = "工单状态流转")
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:transition')")
    public ApiResult<SocTicket> transition(@PathVariable Long id, @Valid @RequestBody TicketTransitionRequest request) {
        return ApiResult.ok(service.transitionTicket(id, request));
    }
}
