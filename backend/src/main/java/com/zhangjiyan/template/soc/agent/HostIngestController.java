package com.zhangjiyan.template.soc.agent;

import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.IngestResult;
import com.zhangjiyan.template.soc.fim.FimWatchPathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SOC 主机真实数据接入", description = "Mac/Windows Host Agent 资产、事件、FIM 和基线上报")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/ingest/host")
public class HostIngestController {

    private final HostAgentService service;

    @Operation(summary = "上报主机资产")
    @PostMapping("/assets")
    public ApiResult<IngestResult> assets(@Valid @RequestBody HostAssetIngestRequest request,
                                          HttpServletRequest servletRequest) {
        return ApiResult.ok(service.ingestAssets(request, agentToken(servletRequest), clientIp(servletRequest)));
    }

    @Operation(summary = "读取当前 Agent 的已授权文件监控目录")
    @GetMapping("/fim-watch-paths")
    public ApiResult<List<FimWatchPathService.AgentWatchPath>> fimWatchPaths(@RequestParam String agentId,
                                                                               @RequestParam String osType,
                                                                               HttpServletRequest servletRequest) {
        return ApiResult.ok(service.authorizedFimWatchPaths(agentId, agentToken(servletRequest), osType));
    }

    @Operation(summary = "上报主机原始安全事件")
    @PostMapping("/events")
    public ApiResult<IngestResult> events(@Valid @RequestBody HostEventIngestRequest request,
                                          HttpServletRequest servletRequest) {
        return ApiResult.ok(service.ingestEvents(request, agentToken(servletRequest), clientIp(servletRequest)));
    }

    @Operation(summary = "上报文件完整性事件")
    @PostMapping("/fim")
    public ApiResult<IngestResult> fim(@Valid @RequestBody HostFimIngestRequest request,
                                       HttpServletRequest servletRequest) {
        return ApiResult.ok(service.ingestFim(request, agentToken(servletRequest), clientIp(servletRequest)));
    }

    @Operation(summary = "上报主机基线检查")
    @PostMapping("/baseline")
    public ApiResult<IngestResult> baseline(@Valid @RequestBody HostBaselineIngestRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.ingestBaseline(request, agentToken(servletRequest), clientIp(servletRequest)));
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
