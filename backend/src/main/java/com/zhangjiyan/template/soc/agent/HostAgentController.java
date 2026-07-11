package com.zhangjiyan.template.soc.agent;

import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentDetail;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentHeartbeatResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentOverview;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRegistrationResult;
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

@Tag(name = "SOC 主机采集器", description = "Mac/Windows Host Agent 注册、心跳和状态查询")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/agents")
public class HostAgentController {

    private final HostAgentService service;
    private final HostAgentRuntimeService runtimeService;
    private final HostAgentLocalInstallService localInstallService;

    @Operation(summary = "管理员注册或轮换 Host Agent Token")
    @PostMapping("/register")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:register')")
    public ApiResult<AgentRegistrationResult> register(@Valid @RequestBody HostAgentRegisterRequest request,
                                                       HttpServletRequest servletRequest) {
        return ApiResult.ok(service.register(request, clientIp(servletRequest)));
    }

    @Operation(summary = "Host Agent 心跳")
    @PostMapping("/heartbeat")
    public ApiResult<AgentHeartbeatResult> heartbeat(@Valid @RequestBody HostAgentHeartbeatRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResult.ok(service.heartbeat(request, agentToken(servletRequest), clientIp(servletRequest)));
    }

    @Operation(summary = "查询 Host Agent 列表")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:view')")
    public ApiResult<List<SocHostAgent>> list(@RequestParam(required = false) String osType,
                                              @RequestParam(required = false) String status) {
        return ApiResult.ok(service.listAgents(osType, status));
    }

    @Operation(summary = "查询 Host Agent 采集概览")
    @GetMapping("/overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:view')")
    public ApiResult<AgentOverview> overview() {
        return ApiResult.ok(service.overview());
    }

    @Operation(summary = "查询 Host Agent 详情")
    @GetMapping("/detail/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:view')")
    public ApiResult<AgentDetail> detail(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id));
    }

    @Operation(summary = "启用或停用 Host Agent")
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:manage')")
    public ApiResult<SocHostAgent> toggleEnabled(@PathVariable Long id,
                                                 @Valid @RequestBody HostAgentToggleRequest request) {
        return ApiResult.ok(service.setEnabled(id, request.enabled()));
    }

    @Operation(summary = "启动或停止本机 Host Agent 运行时")
    @PostMapping("/{id}/runtime")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:manage')")
    public ApiResult<AgentRuntimeResult> controlRuntime(@PathVariable Long id,
                                                        @Valid @RequestBody HostAgentRuntimeRequest request) {
        return ApiResult.ok(runtimeService.control(id, request));
    }

    @Operation(summary = "读取当前后端宿主机的 Host Agent 安装上下文")
    @GetMapping("/local-install/context")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:register')")
    public ApiResult<LocalAgentInstallContext> localInstallContext() {
        return ApiResult.ok(localInstallService.context());
    }

    @Operation(summary = "在当前后端宿主机安装、启动并校验 Host Agent")
    @PostMapping("/local-install")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:agent:manage')")
    public ApiResult<LocalAgentInstallResult> installOnLocalHost(@Valid @RequestBody HostAgentLocalInstallRequest request,
                                                                  HttpServletRequest servletRequest) {
        return ApiResult.ok(localInstallService.install(request, clientIp(servletRequest)));
    }

    private String agentToken(HttpServletRequest request) {
        return request.getHeader(HostAgentService.AGENT_TOKEN_HEADER);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
