package com.zhangjiyan.template.soc.client;

import com.zhangjiyan.template.common.file.FileStorageProperties;
import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Tag(name = "用户端运行环境", description = "面向办公电脑用户端的跨系统适配状态")
@RestController
@RequiredArgsConstructor
@RequestMapping("/client/runtime")
public class ClientRuntimeController {

    private final FileStorageProperties fileStorageProperties;

    @Operation(summary = "用户端本机兼容性与自动适配状态")
    @GetMapping("/compatibility")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<ClientRuntimeCompatibility> compatibility(HttpServletRequest request) {
        String osName = System.getProperty("os.name", "unknown");
        String osFamily = osFamily(osName);
        Path sourceRoot = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path dataRoot = Path.of(fileStorageProperties.getBaseDir()).toAbsolutePath().normalize();
        boolean dataRootOutsideSource = !dataRoot.startsWith(sourceRoot);
        boolean dataRootUsesEnvironment = dataRoot.toString().contains("Environment");
        String dataRootStatus = dataRootOutsideSource && dataRootUsesEnvironment ? "ready" : "warning";

        List<RuntimeCapability> capabilities = new ArrayList<>();
        capabilities.add(new RuntimeCapability(
                "route_context",
                "页面重开上下文",
                "ready",
                "URL query + 浏览器本地上下文双通道保持当前电脑"
        ));
        capabilities.add(new RuntimeCapability(
                "local_terminal_guard",
                "白名单终端观察",
                "ready",
                terminalStrategy(osFamily)
        ));
        capabilities.add(new RuntimeCapability(
                "vm_console_embed",
                "本地 VM 控制台",
                "configurable",
                "支持 localhost / 内网 noVNC 控制台地址嵌入，不连接外部资产"
        ));
        capabilities.add(new RuntimeCapability(
                "data_root_isolation",
                "运行数据隔离",
                dataRootStatus,
                dataRootOutsideSource
                        ? "运行数据目录位于源码目录外"
                        : "运行数据目录疑似位于源码目录内，请迁移到 Environment"
        ));
        capabilities.add(new RuntimeCapability(
                "browser_client",
                "浏览器用户端",
                browserFamily(request.getHeader("User-Agent")).equals("unknown") ? "warning" : "ready",
                browserFamily(request.getHeader("User-Agent")) + " / " + osFamily
        ));

        return ApiResult.ok(new ClientRuntimeCompatibility(
                new RuntimePlatform(
                        osFamily,
                        safeSystemValue(osName),
                        safeSystemValue(System.getProperty("os.arch", "unknown")),
                        majorJavaVersion(System.getProperty("java.version", "unknown")),
                        browserFamily(request.getHeader("User-Agent"))
                ),
                new RuntimeDataRoot(
                        dataRootStatus,
                        dataRootUsesEnvironment,
                        dataRootOutsideSource,
                        dataRoot.getFileName() == null ? "unknown" : dataRoot.getFileName().toString()
                ),
                adapterLabel(osFamily),
                capabilities,
                OffsetDateTime.now().toString()
        ));
    }

    private String osFamily(String osName) {
        String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) return "windows";
        if (normalized.contains("mac") || normalized.contains("darwin")) return "macos";
        if (normalized.contains("linux")) return "linux";
        return "unknown";
    }

    private String adapterLabel(String osFamily) {
        return switch (osFamily) {
            case "windows" -> "Windows PowerShell / WMI safe observer";
            case "macos" -> "macOS launchctl / ps safe observer";
            case "linux" -> "Linux systemd / proc safe observer";
            default -> "Generic browser-only observer";
        };
    }

    private String terminalStrategy(String osFamily) {
        return switch (osFamily) {
            case "windows" -> "Windows 使用 whoami / netstat / tasklist / reg query 白名单观察命令";
            case "macos" -> "macOS 使用 id / lsof / ps / launchctl 白名单观察命令";
            case "linux" -> "Linux 使用 id / ss / ps / systemctl 白名单观察命令";
            default -> "未知系统仅启用网页与手动上报能力";
        };
    }

    private String browserFamily(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("edg/")) return "Edge";
        if (normalized.contains("chrome/") || normalized.contains("chromium/")) return "Chromium";
        if (normalized.contains("firefox/")) return "Firefox";
        if (normalized.contains("safari/")) return "Safari";
        return "unknown";
    }

    private String majorJavaVersion(String javaVersion) {
        if (javaVersion == null || javaVersion.isBlank()) return "unknown";
        String[] parts = javaVersion.split("\\.");
        if (parts.length > 1 && "1".equals(parts[0])) return "Java " + parts[1];
        return "Java " + parts[0];
    }

    private String safeSystemValue(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9 ._/-]", "").trim();
    }

    public record ClientRuntimeCompatibility(
            RuntimePlatform platform,
            RuntimeDataRoot dataRoot,
            String adapter,
            List<RuntimeCapability> capabilities,
            String checkedAt
    ) {
    }

    public record RuntimePlatform(
            String osFamily,
            String osName,
            String arch,
            String javaVersion,
            String browserFamily
    ) {
    }

    public record RuntimeDataRoot(
            String status,
            boolean environmentRoot,
            boolean outsideSourceRoot,
            String displayName
    ) {
    }

    public record RuntimeCapability(
            String key,
            String label,
            String status,
            String message
    ) {
    }
}
