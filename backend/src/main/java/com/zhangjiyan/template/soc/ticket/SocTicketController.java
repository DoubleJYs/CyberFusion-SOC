package com.zhangjiyan.template.soc.ticket;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.playbook.ResponsePlaybookService;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.TaskActionRequest;
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
    private final ResponsePlaybookService playbookService;

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

    @Operation(summary = "工单处置任务")
    @GetMapping("/{id}/tasks")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:view')")
    public ApiResult<java.util.List<SocTicketTask>> tasks(@PathVariable Long id) {
        return ApiResult.ok(playbookService.ticketTasks(id));
    }

    @Operation(summary = "开始处置任务")
    @PostMapping("/{ticketId}/tasks/{taskId}/start")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:transition') or hasAuthority('soc:ticket-task:update')")
    public ApiResult<SocTicketTask> startTask(@PathVariable Long ticketId,
                                              @PathVariable Long taskId,
                                              @RequestBody(required = false) TaskActionRequest request) {
        return ApiResult.ok(playbookService.startTask(taskId, request));
    }

    @Operation(summary = "完成处置任务")
    @PostMapping("/{ticketId}/tasks/{taskId}/complete")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:transition') or hasAuthority('soc:ticket-task:update')")
    public ApiResult<SocTicketTask> completeTask(@PathVariable Long ticketId,
                                                 @PathVariable Long taskId,
                                                 @RequestBody(required = false) TaskActionRequest request) {
        return ApiResult.ok(playbookService.completeTask(taskId, request));
    }

    @Operation(summary = "跳过处置任务")
    @PostMapping("/{ticketId}/tasks/{taskId}/skip")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:ticket:transition') or hasAuthority('soc:ticket-task:update')")
    public ApiResult<SocTicketTask> skipTask(@PathVariable Long ticketId,
                                             @PathVariable Long taskId,
                                             @RequestBody(required = false) TaskActionRequest request) {
        return ApiResult.ok(playbookService.skipTask(taskId, request));
    }
}
