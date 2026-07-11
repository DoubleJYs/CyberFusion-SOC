package com.zhangjiyan.template.soc.agent;

import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRuntimeEnvironment;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallContext;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.LocalAgentInstallStage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes the bundled installer only on the host that runs this backend.
 * The browser never supplies filesystem roots, shell fragments, or Agent tokens.
 */
@Service
@RequiredArgsConstructor
public class HostAgentLocalInstallService {

    private static final Duration INSTALL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(1);

    private final HostAgentService agentService;
    private final SocHostAgentMapper agentMapper;

    @Value("${server.port:18080}")
    private int serverPort;

    public LocalAgentInstallContext context() {
        String osType = HostAgentRuntimeInspector.localOsType();
        String hostname = localHostname();
        AgentRuntimeEnvironment runtime = runtimeEnvironment(osType);
        boolean supported = "macos".equals(osType) || "windows".equals(osType);
        return new LocalAgentInstallContext(
                runtime,
                hostname,
                localIpAddresses(),
                defaultAgentId(osType, hostname),
                runtime.label() + " Host Agent",
                configuredAgentVersion(),
                "http://127.0.0.1:" + serverPort + "/api",
                HostAgentRuntimeInspector.projectRoot().toString(),
                HostAgentRuntimeInspector.envRoot().toString(),
                defaultFimPath(osType).toString(),
                supported,
                supported
                        ? "页面将使用当前后端所在主机的真实系统、目录和安装脚本。"
                        : "当前后端运行环境暂不支持通过页面直接安装 Host Agent。"
        );
    }

    public LocalAgentInstallResult install(HostAgentLocalInstallRequest request, String clientIp) {
        return installForOwner(request, clientIp, null, null);
    }

    /** Used by the user-side My Computer flow after the asset owner was resolved server-side. */
    public LocalAgentInstallResult installForOwner(HostAgentLocalInstallRequest request, String clientIp, Long ownerId, Long deptId) {
        LocalAgentInstallContext context = context();
        if (!context.supported()) {
            throw new BusinessException(context.message());
        }
        validateFimPath(request.fimPath());

        List<LocalAgentInstallStage> stages = new ArrayList<>();
        HostAgentResponses.AgentRegistrationResult registration = ownerId == null
                ? agentService.register(
                new HostAgentRegisterRequest(
                        request.agentId(),
                        request.agentName(),
                        request.hostname(),
                        context.runtime().osType(),
                        context.runtime().osName() + " " + context.runtime().osVersion(),
                        context.runtime().architecture(),
                        request.agentVersion(),
                        distinctAddresses(request.ipAddresses()),
                        List.of(),
                        Map.of("install", "local-ui", "runtimeOs", context.runtime().osType(), "agent", "go", "profile", request.profile())
                ),
                clientIp
        ) : agentService.registerForOwner(
                new HostAgentRegisterRequest(
                        request.agentId(),
                        request.agentName(),
                        request.hostname(),
                        context.runtime().osType(),
                        context.runtime().osName() + " " + context.runtime().osVersion(),
                        context.runtime().architecture(),
                        request.agentVersion(),
                        distinctAddresses(request.ipAddresses()),
                        List.of(),
                        Map.of("install", "local-ui", "runtimeOs", context.runtime().osType(), "agent", "go", "profile", request.profile())
                ),
                clientIp, ownerId, deptId
        );
        stages.add(new LocalAgentInstallStage("建立 Agent 身份", "passed", "Token 已写入本机安装进程，不会回传到页面。"));

        Map<String, String> env = installEnvironment(context, request, registration.agentId(), registration.agentToken());
        runScript(context.runtime().osType(), "install", env, INSTALL_TIMEOUT);
        stages.add(new LocalAgentInstallStage("写入本机运行时", "passed", "二进制和受限配置已写入外部运行根目录。"));

        LocalDateTime beforeStart = LocalDateTime.now().minusSeconds(1);
        runScript(context.runtime().osType(), "start", env, INSTALL_TIMEOUT);
        stages.add(new LocalAgentInstallStage("启动 Agent", "passed", "已执行当前主机的常驻采集器启动命令。"));

        env.put("CYBERFUSION_AGENT_UPLOAD_ONCE", "1");
        runScript(context.runtime().osType(), "verify", env, VERIFY_TIMEOUT);
        stages.add(new LocalAgentInstallStage("执行真实校验", "passed", "已完成平台健康检查和一次真实主机上报。"));

        SocHostAgent agent = waitForFreshHeartbeat(registration.agentId(), beforeStart);
        boolean heartbeatReceived = agent != null
                && "online".equalsIgnoreCase(agent.getStatus())
                && agent.getLastSeenAt() != null
                && !agent.getLastSeenAt().isBefore(beforeStart);
        stages.add(new LocalAgentInstallStage("等待新心跳", heartbeatReceived ? "passed" : "pending",
                heartbeatReceived ? "平台已收到本次安装后的新心跳。" : "安装进程已启动，尚未收到新的心跳。"));
        boolean installed = agent != null && HostAgentRuntimeInspector.inspect(agent, context.runtime().osType()).controllable();
        return new LocalAgentInstallResult(
                registration.agentId(),
                installed,
                heartbeatReceived,
                heartbeatReceived ? "online" : "verification_pending",
                List.copyOf(stages),
                heartbeatReceived ? "Agent 已在当前主机安装、启动并收到新的心跳。" : "安装进程已启动，等待本机 Agent 回传新的心跳后才会标记为启动成功。"
        );
    }

    private void runScript(String osType, String action, Map<String, String> environment, Duration timeout) {
        Path script = scriptPath(osType, action);
        if (!Files.isRegularFile(script)) {
            throw new BusinessException("本机 Agent " + action + " 脚本不存在。");
        }
        List<String> command = commandFor(osType, script, action, environment);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(HostAgentRuntimeInspector.projectRoot().toFile());
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException("本机 Agent " + action + " 超时，请在 Agent 管理页查看运行状态。");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException("本机 Agent " + action + " 执行失败，请检查运行目录日志和依赖环境。");
            }
        } catch (IOException ex) {
            throw new BusinessException("本机 Agent " + action + " 无法启动。");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("本机 Agent " + action + " 被中断。");
        }
    }

    private Map<String, String> installEnvironment(LocalAgentInstallContext context,
                                                    HostAgentLocalInstallRequest request,
                                                    String agentId,
                                                    String agentToken) {
        Map<String, String> environment = new java.util.HashMap<>();
        environment.put("CYBERFUSION_ENV_ROOT", context.envRoot());
        environment.put("CYBERFUSION_API_BASE", context.apiBaseUrl());
        environment.put("CYBERFUSION_AGENT_ID", agentId);
        environment.put("CYBERFUSION_AGENT_TOKEN", agentToken);
        environment.put("CYBERFUSION_AGENT_VERSION", request.agentVersion());
        environment.put("CYBERFUSION_AGENT_PROFILE", request.profile());
        environment.put("CYBERFUSION_AGENT_FIM_PATH", request.fimPath());
        environment.put("CYBERFUSION_AGENT_INTERVAL", request.interval());
        environment.put("CYBERFUSION_AGENT_LAUNCHD_SCOPE", "user");
        environment.put("CYBERFUSION_AGENT_SERVICE_NAME", "CyberFusionHostAgent");
        return environment;
    }

    private List<String> commandFor(String osType, Path script, String action, Map<String, String> environment) {
        if ("windows".equals(osType)) {
            List<String> command = new ArrayList<>(List.of(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()
            ));
            if ("start".equals(action)) {
                command.addAll(List.of("-AgentId", environment.get("CYBERFUSION_AGENT_ID"), "-ServiceName", "CyberFusionHostAgent"));
            } else if ("verify".equals(action)) {
                command.addAll(List.of("-AgentId", environment.get("CYBERFUSION_AGENT_ID"), "-ServiceName", "CyberFusionHostAgent", "-UploadOnce"));
            }
            return command;
        }
        return List.of(script.toString());
    }

    private Path scriptPath(String osType, String action) {
        String directory = "macos".equals(osType) ? "scripts/mac" : "scripts/win";
        String filename = switch (action) {
            case "install" -> "macos".equals(osType) ? "install-agent.sh" : "install-agent.ps1";
            case "start" -> "macos".equals(osType) ? "start-agent.sh" : "start-agent.ps1";
            case "verify" -> "macos".equals(osType) ? "verify-agent.sh" : "verify-agent.ps1";
            default -> throw new BusinessException("不支持的本机 Agent 操作。");
        };
        return HostAgentRuntimeInspector.projectRoot().resolve(directory).resolve(filename);
    }

    private AgentRuntimeEnvironment runtimeEnvironment(String osType) {
        return new AgentRuntimeEnvironment(
                osType,
                switch (osType) {
                    case "macos" -> "macOS";
                    case "windows" -> "Windows";
                    case "linux" -> "Linux";
                    default -> "Host OS";
                },
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", ""),
                System.getProperty("os.arch", "")
        );
    }

    private String localHostname() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return hostname == null || hostname.isBlank() ? "local-host" : hostname;
        } catch (IOException ex) {
            return "local-host";
        }
    }

    private List<String> localIpAddresses() {
        Set<String> values = new LinkedHashSet<>();
        try {
            NetworkInterface.networkInterfaces()
                    .filter(network -> isActiveNonLoopback(network))
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                    .map(address -> address.getHostAddress())
                    .filter(address -> address != null && !address.isBlank())
                    .forEach(values::add);
        } catch (SocketException ignored) {
            // Hostnames and an empty address list remain valid for local installation.
        }
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private boolean isActiveNonLoopback(NetworkInterface network) {
        try {
            return network.isUp() && !network.isLoopback() && !network.isVirtual();
        } catch (SocketException ex) {
            return false;
        }
    }

    private String defaultAgentId(String osType, String hostname) {
        String normalizedHost = hostname.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "-");
        normalizedHost = normalizedHost.replaceAll("^-+|-+$", "");
        if (normalizedHost.isBlank()) {
            normalizedHost = "host";
        }
        return ("cyberfusion-" + osType + "-" + normalizedHost).substring(0,
                Math.min(128, ("cyberfusion-" + osType + "-" + normalizedHost).length()));
    }

    private Path defaultFimPath(String osType) {
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        if ("macos".equals(osType)) {
            Path documents = home.resolve("Documents");
            return Files.isDirectory(documents) ? documents : home;
        }
        if ("windows".equals(osType)) {
            Path publicDocuments = Path.of("C:\\Users\\Public\\Documents");
            return Files.isDirectory(publicDocuments) ? publicDocuments : home;
        }
        return home;
    }

    private String configuredAgentVersion() {
        String configured = System.getenv("CYBERFUSION_AGENT_VERSION");
        return configured == null || configured.isBlank() ? "0.1.0-dev" : configured.trim();
    }

    private List<String> distinctAddresses(List<String> addresses) {
        if (addresses == null) {
            return List.of();
        }
        return addresses.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(32)
                .toList();
    }

    private void validateFimPath(String value) {
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isDirectory(path)) {
                throw new BusinessException("FIM 路径必须是当前主机存在的目录。");
            }
        } catch (java.nio.file.InvalidPathException ex) {
            throw new BusinessException("FIM 路径格式无效。");
        }
    }

    private SocHostAgent waitForFreshHeartbeat(String agentId, java.time.LocalDateTime after) {
        for (int i = 0; i < 8; i++) {
            SocHostAgent agent = agentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SocHostAgent>()
                    .eq(SocHostAgent::getAgentId, agentId)
                    .last("LIMIT 1"));
            if (agent != null && "online".equalsIgnoreCase(agent.getStatus())
                    && agent.getLastSeenAt() != null && !agent.getLastSeenAt().isBefore(after)) {
                return agent;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return agentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SocHostAgent>()
                .eq(SocHostAgent::getAgentId, agentId)
                .last("LIMIT 1"));
    }
}
