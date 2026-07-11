package com.zhangjiyan.template.soc.client;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.policy.LocalCheckPolicyService;
import com.zhangjiyan.template.soc.playbook.ResponsePlaybookService;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.TaskActionRequest;
import com.zhangjiyan.template.soc.risk.RiskScoringService;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRuntimeResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallContext;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户端本地演练", description = "面向本地授权网站/虚拟机的演练数据采集")
@RestController
@RequiredArgsConstructor
@RequestMapping("/client")
public class ClientSecurityLabController {

    private final SocOperationService service;
    private final ResponsePlaybookService playbookService;
    private final RiskScoringService riskScoringService;
    private final ClientProtectionService protectionService;

    @Operation(summary = "用户端可见电脑清单")
    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<PageResult<SocAsset>> devices() {
        SocPageRequest request = new SocPageRequest(1, 100, null, null, null, null, null, null, null, null, null, null, null, null);
        return ApiResult.ok(service.assets(request));
    }

    @Operation(summary = "单台电脑安全画像")
    @GetMapping("/devices/{ip}/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocOperationService.ClientDeviceProfile> profile(@PathVariable String ip) {
        return ApiResult.ok(service.clientDeviceProfile(ip));
    }

    @Operation(summary = "单台电脑风险评分画像")
    @GetMapping("/devices/{ip}/risk-profile")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<RiskScoringService.AssetRiskProfile> riskProfile(@PathVariable String ip) {
        return ApiResult.ok(riskScoringService.clientProfile(ip));
    }

    @Operation(summary = "用户端本机保护状态")
    @GetMapping("/protection/status")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<ClientProtectionService.ClientProtectionStatus> protectionStatus(@RequestParam(required = false) String assetIp) {
        return ApiResult.ok(protectionService.status(assetIp));
    }

    @Operation(summary = "读取当前用户电脑的本机 Agent 安装上下文")
    @GetMapping("/protection/install-context")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<LocalAgentInstallContext> protectionInstallContext(@RequestParam String assetIp) {
        return ApiResult.ok(protectionService.installContext(assetIp));
    }

    @Operation(summary = "用户在自己的当前电脑安装并校验 Agent")
    @PostMapping("/protection/install")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<LocalAgentInstallResult> protectionInstall(@Valid @RequestBody ClientProtectionInstallRequest request,
                                                                 HttpServletRequest servletRequest) {
        return ApiResult.ok(protectionService.install(request, servletRequest.getRemoteAddr()));
    }

    @Operation(summary = "用户启动或停止自己当前电脑的 Agent")
    @PostMapping("/protection/runtime")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<AgentRuntimeResult> protectionRuntime(@RequestParam String assetIp, @RequestParam String action) {
        return ApiResult.ok(protectionService.control(assetIp, action));
    }

    @Operation(summary = "提交本地授权演练事件")
    @PostMapping("/lab/events")
    @OperationAudit("CLIENT_LAB.EVENT")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocOperationService.ClientLabEventResult> labEvent(@Valid @RequestBody ClientLabEventRequest request) {
        return ApiResult.ok(service.submitClientLabEvent(request));
    }

    @Operation(summary = "提交本机回环网页演练事件")
    @PostMapping("/lab/local-events")
    public ApiResult<SocOperationService.ClientLabEventResult> localLabEvent(@Valid @RequestBody ClientLabEventRequest request,
                                                                             HttpServletRequest servletRequest) {
        return ApiResult.ok(service.submitLocalDemoLabEvent(request, servletRequest.getRemoteAddr()));
    }

    @Operation(summary = "采集本地电脑安全快照")
    @PostMapping("/local-snapshot/run")
    @OperationAudit("CLIENT_SNAPSHOT.RUN")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocOperationService.ClientSecuritySnapshotResult> runSecuritySnapshot(@Valid @RequestBody ClientSnapshotRequest request) {
        return ApiResult.ok(service.runClientSecuritySnapshot(request));
    }

    @Operation(summary = "采集本机回环安全快照")
    @PostMapping("/local-snapshot/local-run")
    public ApiResult<SocOperationService.ClientSecuritySnapshotResult> runLocalSecuritySnapshot(@Valid @RequestBody ClientSnapshotRequest request,
                                                                                                HttpServletRequest servletRequest) {
        return ApiResult.ok(service.runLocalDemoSecuritySnapshot(request, servletRequest.getRemoteAddr()));
    }

    @Operation(summary = "查询当前系统可用本机检查项")
    @GetMapping("/local-terminal/commands")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<LocalCheckPolicyService.ClientCommandOption>> localTerminalCommands(@RequestParam(defaultValue = "Linux") String os) {
        return ApiResult.ok(service.localCheckCommands(os));
    }

    @Operation(summary = "运行本地授权终端观察命令")
    @PostMapping("/local-terminal/run")
    @OperationAudit("CLIENT_TERMINAL.RUN")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocOperationService.ClientTerminalCommandResult> runTerminalCommand(@Valid @RequestBody ClientTerminalCommandRequest request) {
        return ApiResult.ok(service.runClientTerminalCommand(request));
    }

    @Operation(summary = "运行本机回环演示终端观察命令")
    @PostMapping("/local-terminal/local-run")
    public ApiResult<SocOperationService.ClientTerminalCommandResult> runLocalTerminalCommand(@Valid @RequestBody ClientTerminalCommandRequest request,
                                                                                              HttpServletRequest servletRequest) {
        return ApiResult.ok(service.runLocalDemoTerminalCommand(request, servletRequest.getRemoteAddr()));
    }

    @Operation(summary = "员工端我的待办任务")
    @GetMapping("/tasks")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<SocTicketTask>> tasks() {
        return ApiResult.ok(playbookService.employeeTasks());
    }

    @Operation(summary = "员工端待办详情")
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocTicketTask> taskDetail(@PathVariable Long taskId) {
        return ApiResult.ok(playbookService.employeeTaskDetail(taskId));
    }

    @Operation(summary = "员工提交待办证据")
    @PostMapping("/tasks/{taskId}/submit-evidence")
    @OperationAudit("CLIENT_TASK.SUBMIT_EVIDENCE")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocTicketTask> submitTaskEvidence(@PathVariable Long taskId,
                                                       @RequestBody(required = false) TaskActionRequest request) {
        return ApiResult.ok(playbookService.submitEmployeeEvidence(taskId, request));
    }

    @Operation(summary = "员工确认待办")
    @PostMapping("/tasks/{taskId}/confirm")
    @OperationAudit("CLIENT_TASK.CONFIRM")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SocTicketTask> confirmTask(@PathVariable Long taskId,
                                                @RequestBody(required = false) TaskActionRequest request) {
        return ApiResult.ok(playbookService.confirmEmployeeTask(taskId, request));
    }
}
