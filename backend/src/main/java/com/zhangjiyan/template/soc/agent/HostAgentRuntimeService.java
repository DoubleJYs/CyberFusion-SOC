package com.zhangjiyan.template.soc.agent;

import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRuntimeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class HostAgentRuntimeService {

    private static final long COMMAND_TIMEOUT_SECONDS = 30;

    private final SocHostAgentMapper agentMapper;
    private final SocSecurityScope securityScope;

    // Compatibility constructor for command-execution unit tests.
    public HostAgentRuntimeService(SocHostAgentMapper agentMapper) {
        this(agentMapper, null);
    }

    public AgentRuntimeResult control(Long id, HostAgentRuntimeRequest request) {
        SocHostAgent agent = agentMapper.selectById(id);
        if (agent == null || Integer.valueOf(1).equals(agent.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Agent 不存在");
        }
        if (securityScope != null && !securityScope.canAccessSelectedWorkspace(agent.getOwnerId(), agent.getDeptId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Agent 不存在");
        }
        String action = normalizeAction(request.action());
        validateControllableAgent(agent);

        if ("start".equals(action)) {
            return startAgent(agent);
        }
        return stopAgent(agent);
    }

    /** Runtime capability is derived on the backend host and never trusted from the browser. */
    public boolean isControllable(SocHostAgent agent) {
        return agent != null && runtimeInstallStatus(agent).controllable();
    }

    private AgentRuntimeResult startAgent(SocHostAgent agent) {
        HostAgentRuntimeInspector.RuntimeInstallStatus installStatus = runtimeInstallStatus(agent);
        if (!installStatus.controllable()) {
            throw new BusinessException(installStatus.reason());
        }
        boolean wasEnabled = isEnabled(agent);
        LocalDateTime beforeStart = LocalDateTime.now().minusSeconds(1);
        updateAcceptance(agent, true);
        try {
            CommandOutcome command = executeRuntimeCommand(agent, "start");
            SocHostAgent refreshed = waitForFreshHeartbeat(agent.getId(), beforeStart);
            boolean online = refreshed != null && "online".equalsIgnoreCase(refreshed.getStatus());
            return new AgentRuntimeResult(
                    agent.getAgentId(),
                    "start",
                    online ? "online" : "starting",
                    true,
                    true,
                    online ? "Agent 已启动并收到心跳。" : "Agent 启动命令已执行，正在等待下一次心跳。",
                    LocalDateTime.now()
            );
        } catch (BusinessException ex) {
            if (!wasEnabled) {
                updateAcceptance(agent, false);
            }
            throw ex;
        }
    }

    private AgentRuntimeResult stopAgent(SocHostAgent agent) {
        boolean commandExecuted = true;
        String message = "Agent 已停止，平台已关闭接收。";
        HostAgentRuntimeInspector.RuntimeInstallStatus installStatus = runtimeInstallStatus(agent);
        if (installStatus.controllable()) {
            try {
                executeRuntimeCommand(agent, "stop");
            } catch (BusinessException ex) {
                commandExecuted = false;
                message = "平台已关闭接收；本机停止命令未完成：" + ex.getMessage();
            }
        } else {
            commandExecuted = false;
            message = "平台已关闭接收；" + installStatus.reason();
        }
        updateAcceptance(agent, false);
        return new AgentRuntimeResult(
                agent.getAgentId(),
                "stop",
                "stopped",
                false,
                commandExecuted,
                message,
                LocalDateTime.now()
        );
    }

    private void updateAcceptance(SocHostAgent agent, boolean enabled) {
        agent.setEnabled(enabled ? 1 : 0);
        agent.setStatus(enabled ? "offline" : "disabled");
        agentMapper.updateById(agent);
    }

    private SocHostAgent waitForFreshHeartbeat(Long id, LocalDateTime after) {
        for (int i = 0; i < 5; i++) {
            SocHostAgent current = agentMapper.selectById(id);
            if (current != null
                    && "online".equalsIgnoreCase(current.getStatus())
                    && current.getLastSeenAt() != null
                    && !current.getLastSeenAt().isBefore(after)) {
                return current;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return current;
            }
        }
        return agentMapper.selectById(id);
    }

    protected CommandOutcome executeRuntimeCommand(SocHostAgent agent, String action) {
        List<String> command = runtimeCommand(agent, action);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectRoot().toFile());
        prepareRuntimeEnvironment(builder.environment(), agent);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("Agent " + action + " command timed out");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new BusinessException(safeOutput(stderr.isBlank() ? stdout : stderr));
            }
            return new CommandOutcome(process.exitValue(), safeOutput(stdout));
        } catch (IOException ex) {
            throw new BusinessException("Agent " + action + " command failed to start");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Agent " + action + " command interrupted");
        }
    }

    protected void prepareRuntimeEnvironment(Map<String, String> env, SocHostAgent agent) {
        env.put("CYBERFUSION_AGENT_ID", agent.getAgentId());
        env.put("CYBERFUSION_ENV_ROOT", HostAgentRuntimeInspector.envRoot().toString());
    }

    private List<String> runtimeCommand(SocHostAgent agent, String action) {
        String osType = normalizeOs(agent.getOsType());
        Path script = scriptPath(osType, action);
        if (!Files.isRegularFile(script)) {
            throw new BusinessException("Agent runtime script not found: " + script.getFileName());
        }
        if ("windows".equals(osType)) {
            return List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(), "-AgentId", agent.getAgentId());
        }
        return List.of(script.toString());
    }

    private Path scriptPath(String osType, String action) {
        Path root = projectRoot();
        if ("macos".equals(osType)) {
            return root.resolve("scripts/mac/" + ("start".equals(action) ? "start-agent.sh" : "stop-agent.sh"));
        }
        if ("windows".equals(osType)) {
            return root.resolve("scripts/win/" + ("start".equals(action) ? "start-agent.ps1" : "stop-agent.ps1"));
        }
        throw new BusinessException("当前仅支持 macOS 和 Windows Agent 本机启停");
    }

    protected Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if ("backend".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        return current;
    }

    private void validateControllableAgent(SocHostAgent agent) {
        if (!HostAgentRuntimeInspector.isSafeAgentId(agent.getAgentId())) {
            throw new BusinessException("Agent ID 包含非法字符，拒绝执行本机启停");
        }
        String localOs = localOsType();
        String agentOs = normalizeOs(agent.getOsType());
        if (!localOs.equals(agentOs)) {
            throw new BusinessException("该 Agent 不在当前后端宿主机上，不能通过本页面直接启停");
        }
    }

    protected HostAgentRuntimeInspector.RuntimeInstallStatus runtimeInstallStatus(SocHostAgent agent) {
        return HostAgentRuntimeInspector.inspect(agent, localOsType());
    }

    protected String localOsType() {
        return HostAgentRuntimeInspector.localOsType();
    }

    private boolean isEnabled(SocHostAgent agent) {
        return !Integer.valueOf(0).equals(agent.getEnabled()) && !"disabled".equalsIgnoreCase(agent.getStatus());
    }

    private String normalizeAction(String action) {
        String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!"start".equals(value) && !"stop".equals(value)) {
            throw new BusinessException("Agent 操作只支持 start 或 stop");
        }
        return value;
    }

    private String normalizeOs(String osType) {
        return HostAgentRuntimeInspector.normalizeOs(osType);
    }

    private String safeOutput(String value) {
        if (value == null || value.isBlank()) {
            return "Agent runtime command failed";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    protected record CommandOutcome(int exitCode, String output) {
    }
}
