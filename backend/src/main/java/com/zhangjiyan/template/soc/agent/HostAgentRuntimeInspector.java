package com.zhangjiyan.template.soc.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

final class HostAgentRuntimeInspector {

    private HostAgentRuntimeInspector() {
    }

    static RuntimeInstallStatus inspect(SocHostAgent agent, String localOsType) {
        if (agent == null || !isSafeAgentId(agent.getAgentId())) {
            return new RuntimeInstallStatus(false, "unsafe_id", "Agent ID 包含非法字符，不能执行本机启停。");
        }
        String agentOs = normalizeOs(agent.getOsType());
        if (!agentOs.equals(localOsType)) {
            return new RuntimeInstallStatus(false, "unsupported_os", "该 Agent 不在当前后端宿主机上，不能通过本页面直接启停。");
        }
        if ("macos".equals(agentOs)) {
            return inspectMacos(agent);
        }
        if ("windows".equals(agentOs)) {
            return inspectWindows(agent);
        }
        return new RuntimeInstallStatus(false, "unsupported_os", "当前仅支持 macOS 和 Windows Agent 本机启停。");
    }

    static String localOsType() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return "macos";
        }
        if (osName.contains("win")) {
            return "windows";
        }
        return "linux";
    }

    static String normalizeOs(String osType) {
        String value = osType == null ? "" : osType.trim().toLowerCase(Locale.ROOT);
        if ("darwin".equals(value) || "mac".equals(value)) {
            return "macos";
        }
        return value;
    }

    static boolean isSafeAgentId(String agentId) {
        return agentId != null && agentId.matches("[A-Za-z0-9_.-]{1,128}");
    }

    private static RuntimeInstallStatus inspectMacos(SocHostAgent agent) {
        Path agentRoot = envRoot().resolve("agent").resolve(agent.getAgentId());
        Path binary = agentRoot.resolve("bin/cyberfusion-agent");
        Path config = agentRoot.resolve("config/agent.env");
        if (!Files.isRegularFile(binary) || !Files.isExecutable(binary)) {
            return new RuntimeInstallStatus(false, "missing_runtime", "本机未安装该 Agent 运行时，仅保留历史上报记录。");
        }
        if (!Files.isRegularFile(config)) {
            return new RuntimeInstallStatus(false, "missing_config", "本机缺少该 Agent 配置文件，请先重新安装 Agent。");
        }
        return new RuntimeInstallStatus(true, "installed", "本机运行时已安装，可以通过页面启停。");
    }

    private static RuntimeInstallStatus inspectWindows(SocHostAgent agent) {
        Path agentRoot = envRoot().resolve("agent").resolve(agent.getAgentId());
        Path binary = agentRoot.resolve("bin/cyberfusion-agent.exe");
        Path config = agentRoot.resolve("config/agent.env");
        if (!Files.isRegularFile(binary)) {
            return new RuntimeInstallStatus(false, "missing_runtime", "本机未安装该 Windows Agent 运行时，仅保留验收预留或历史记录。");
        }
        if (!Files.isRegularFile(config)) {
            return new RuntimeInstallStatus(false, "missing_config", "本机缺少该 Windows Agent 配置文件，请先重新安装 Agent。");
        }
        return new RuntimeInstallStatus(true, "installed", "本机运行时已安装，可以通过页面启停。");
    }

    static Path envRoot() {
        String configured = System.getenv("CYBERFUSION_ENV_ROOT");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), "Environment", "cyberfusion-platform")
                .toAbsolutePath()
                .normalize();
    }

    static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (current.getFileName() != null && "backend".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        return current;
    }

    record RuntimeInstallStatus(boolean controllable, String status, String reason) {
    }
}
