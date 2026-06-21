package com.zhangjiyan.template.soc.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocalCheckPolicyService {

    private static final TypeReference<List<String>> ARGV_TYPE = new TypeReference<>() {
    };
    private static final Set<String> FORBIDDEN_EXECUTABLES = Set.of(
            "sh", "bash", "zsh", "cmd", "powershell", "pwsh", "sudo", "su",
            "rm", "rmdir", "del", "erase", "format", "chmod", "chown",
            "curl", "wget", "ftp", "scp", "ssh", "nc", "netcat",
            "nmap", "masscan", "hydra", "sqlmap", "msfconsole", "metasploit",
            "python", "python3", "perl", "ruby", "node", "npm", "pip", "pip3",
            "apt", "apt-get", "yum", "dnf", "brew", "docker", "kubectl"
    );

    private final SocLocalCheckCommandMapper commandMapper;
    private final ObjectMapper objectMapper;
    private final SocSecurityScope securityScope;

    public PageResult<SocLocalCheckCommand> page(long pageNum, long pageSize, String osType, String status, String keyword) {
        LambdaQueryWrapper<SocLocalCheckCommand> wrapper = baseWrapper()
                .eq(hasText(osType), SocLocalCheckCommand::getOsType, normalizeOsType(osType))
                .eq(hasText(status), SocLocalCheckCommand::getStatus, status)
                .and(hasText(keyword), w -> w.like(SocLocalCheckCommand::getCommandKey, keyword)
                        .or().like(SocLocalCheckCommand::getDisplayName, keyword)
                        .or().like(SocLocalCheckCommand::getDescription, keyword))
                .orderByAsc(SocLocalCheckCommand::getOsType, SocLocalCheckCommand::getSortOrder)
                .orderByDesc(SocLocalCheckCommand::getUpdatedAt);
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.max(1, pageSize);
        try {
            return PageResult.from(commandMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper));
        } catch (RuntimeException ex) {
            return fallbackPage(safePageNum, safePageSize, osType, status, keyword);
        }
    }

    public List<ClientCommandOption> clientCommands(String osType) {
        String normalizedOs = normalizeOsType(osType);
        List<SocLocalCheckCommand> dbCommands = activeCommands(normalizedOs);
        if (dbCommands.isEmpty()) {
            return defaultCommands(normalizedOs).stream()
                    .map(command -> command.toClientOption(true))
                    .toList();
        }
        return dbCommands.stream()
                .map(this::toResolvedCommand)
                .map(command -> command.toClientOption(false))
                .toList();
    }

    public List<ResolvedCommand> snapshotCommands(String osType) {
        String normalizedOs = normalizeOsType(osType);
        List<SocLocalCheckCommand> dbCommands = activeCommands(normalizedOs);
        if (dbCommands.isEmpty()) {
            return defaultCommands(normalizedOs);
        }
        return dbCommands.stream().map(this::toResolvedCommand).toList();
    }

    public ResolvedCommand resolve(String commandKey, String osType) {
        String normalizedKey = requireCommandKey(commandKey);
        String normalizedOs = normalizeOsType(osType);
        List<SocLocalCheckCommand> activeForOs = activeCommands(normalizedOs);
        if (activeForOs.isEmpty()) {
            return defaultCommands(normalizedOs).stream()
                    .filter(command -> command.commandKey().equals(normalizedKey))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("不支持的本机检查策略"));
        }
        return activeForOs.stream()
                .filter(command -> normalizedKey.equals(command.getCommandKey()))
                .findFirst()
                .map(this::toResolvedCommand)
                .orElseThrow(() -> new BusinessException("不支持的本机检查策略或系统类型不匹配"));
    }

    @Transactional
    public SocLocalCheckCommand create(LocalCheckCommandRequest request) {
        validateRequest(request);
        SocLocalCheckCommand command = new SocLocalCheckCommand();
        applyRequest(command, request, false);
        command.setVersion(1);
        command.setCreatedBy(securityScope.currentUserId());
        command.setUpdatedBy(securityScope.currentUserId());
        commandMapper.insert(command);
        return command;
    }

    @Transactional
    public SocLocalCheckCommand update(Long id, LocalCheckCommandRequest request) {
        validateRequest(request);
        SocLocalCheckCommand command = requireCommand(id);
        applyRequest(command, request, true);
        command.setUpdatedBy(securityScope.currentUserId());
        command.setVersion((command.getVersion() == null ? 1 : command.getVersion()) + 1);
        commandMapper.updateById(command);
        return command;
    }

    @Transactional
    public SocLocalCheckCommand publish(Long id) {
        SocLocalCheckCommand command = requireCommand(id);
        validateCommandArgv(command.getCommandArgvJson());
        command.setStatus("active");
        command.setEnabled(1);
        command.setApprovedBy(securityScope.currentUserId());
        command.setApprovedAt(LocalDateTime.now());
        command.setUpdatedBy(securityScope.currentUserId());
        command.setVersion((command.getVersion() == null ? 1 : command.getVersion()) + 1);
        commandMapper.updateById(command);
        return command;
    }

    @Transactional
    public SocLocalCheckCommand changeEnabled(Long id, boolean enabled) {
        SocLocalCheckCommand command = requireCommand(id);
        command.setEnabled(enabled ? 1 : 0);
        if (!enabled) {
            command.setStatus("disabled");
        }
        command.setUpdatedBy(securityScope.currentUserId());
        commandMapper.updateById(command);
        return command;
    }

    @Transactional
    public SocLocalCheckCommand disable(Long id) {
        return changeEnabled(id, false);
    }

    public PrecheckResult precheck(LocalCheckCommandRequest request) {
        validateRequest(request);
        return new PrecheckResult(true, "安全校验通过：该策略只包含 argv 形式的只读检查命令。", validateCommandArgv(request.commandArgvJson()));
    }

    public PrecheckResult validateExisting(Long id) {
        SocLocalCheckCommand command = requireCommand(id);
        List<String> argv = validateCommandArgv(command.getCommandArgvJson());
        return new PrecheckResult(true, "安全校验通过：该策略只包含 argv 形式的只读检查命令。", argv);
    }

    public List<SocLocalCheckCommand> audit() {
        try {
            return commandMapper.selectList(baseWrapper()
                    .orderByDesc(SocLocalCheckCommand::getUpdatedAt)
                    .last("LIMIT 30"));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private SocLocalCheckCommand requireCommand(Long id) {
        SocLocalCheckCommand command = commandMapper.selectById(id);
        if (command == null || Objects.equals(command.getDeleted(), 1)) {
            throw new BusinessException("本机检查策略不存在");
        }
        return command;
    }

    private void applyRequest(SocLocalCheckCommand command, LocalCheckCommandRequest request, boolean keepPublishedState) {
        command.setCommandKey(requireCommandKey(request.commandKey()));
        command.setDisplayName(request.displayName().trim());
        command.setOsType(normalizeOsType(request.osType()));
        command.setCategory(request.category().trim());
        command.setDescription(trimToNull(request.description()));
        command.setCommandArgvJson(writeArgvJson(validateCommandArgv(request.commandArgvJson())));
        command.setTimeoutSeconds(request.timeoutSeconds() == null ? 2 : request.timeoutSeconds());
        command.setOutputLimitKb(request.outputLimitKb() == null ? 8 : request.outputLimitKb());
        command.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        command.setStatus(statusOrDefault(request.status(), keepPublishedState ? command.getStatus() : "draft"));
        command.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        command.setSafetyNote(trimToNull(request.safetyNote()));
        command.setDeleted(0);
    }

    private void validateRequest(LocalCheckCommandRequest request) {
        requireCommandKey(request.commandKey());
        normalizeOsType(request.osType());
        validateCommandArgv(request.commandArgvJson());
    }

    private ResolvedCommand toResolvedCommand(SocLocalCheckCommand command) {
        List<String> argv = validateCommandArgv(command.getCommandArgvJson());
        return new ResolvedCommand(
                command.getCommandKey(),
                command.getDisplayName(),
                normalizeOsType(command.getOsType()),
                command.getCategory(),
                command.getDescription(),
                argv,
                String.join(" ", argv),
                timeout(command.getTimeoutSeconds()),
                outputLimitKb(command.getOutputLimitKb()),
                severityFor(command.getCategory()),
                command.getSortOrder() == null ? 100 : command.getSortOrder()
        );
    }

    private List<SocLocalCheckCommand> activeCommands(String osType) {
        List<SocLocalCheckCommand> rows;
        try {
            rows = commandMapper.selectList(baseWrapper()
                    .eq(SocLocalCheckCommand::getOsType, normalizeOsType(osType))
                    .eq(SocLocalCheckCommand::getStatus, "active")
                    .eq(SocLocalCheckCommand::getEnabled, 1)
                    .orderByAsc(SocLocalCheckCommand::getSortOrder, SocLocalCheckCommand::getId));
        } catch (RuntimeException ex) {
            return List.of();
        }
        return rows == null ? List.of() : rows.stream()
                .filter(row -> Objects.equals(row.getDeleted(), 0) || row.getDeleted() == null)
                .filter(row -> Objects.equals(row.getEnabled(), 1))
                .filter(row -> "active".equals(row.getStatus()))
                .filter(row -> normalizeOsType(osType).equals(normalizeOsType(row.getOsType())))
                .sorted(Comparator.comparing(row -> row.getSortOrder() == null ? 100 : row.getSortOrder()))
                .toList();
    }

    private PageResult<SocLocalCheckCommand> fallbackPage(long pageNum, long pageSize, String osType, String status, String keyword) {
        if (hasText(status) && !"active".equals(status)) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }
        List<String> osTypes = hasText(osType) ? List.of(normalizeOsType(osType)) : List.of("Linux", "macOS", "Windows");
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT).trim();
        List<SocLocalCheckCommand> rows = osTypes.stream()
                .flatMap(os -> defaultCommands(os).stream())
                .map(this::fallbackCommandRow)
                .filter(row -> !hasText(normalizedKeyword)
                        || row.getCommandKey().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || row.getDisplayName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || row.getDescription().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
        int from = (int) Math.min(rows.size(), (pageNum - 1) * pageSize);
        int to = (int) Math.min(rows.size(), from + pageSize);
        return new PageResult<>(rows.subList(from, to), rows.size(), pageNum, pageSize);
    }

    private SocLocalCheckCommand fallbackCommandRow(ResolvedCommand command) {
        SocLocalCheckCommand row = new SocLocalCheckCommand();
        row.setId(-Math.abs((command.osType() + ":" + command.commandKey()).hashCode()) * 1L);
        row.setCommandKey(command.commandKey());
        row.setDisplayName(command.displayName());
        row.setOsType(command.osType());
        row.setCategory(command.category());
        row.setDescription(command.description());
        row.setCommandArgvJson(writeArgvJson(command.argv()));
        row.setTimeoutSeconds(command.timeoutSeconds());
        row.setOutputLimitKb(command.outputLimitKb());
        row.setEnabled(1);
        row.setStatus("active");
        row.setVersion(1);
        row.setSortOrder(command.sortOrder());
        row.setSafetyNote("使用内置默认策略：数据库策略表不可用或尚未初始化。");
        row.setDeleted(0);
        return row;
    }

    private LambdaQueryWrapper<SocLocalCheckCommand> baseWrapper() {
        return new LambdaQueryWrapper<SocLocalCheckCommand>().eq(SocLocalCheckCommand::getDeleted, 0);
    }

    private List<String> validateCommandArgv(String commandArgvJson) {
        List<String> argv;
        try {
            argv = objectMapper.readValue(commandArgvJson, ARGV_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("commandArgvJson 必须是 JSON 字符串数组");
        }
        if (argv == null || argv.isEmpty() || argv.size() > 12) {
            throw new BusinessException("commandArgvJson 必须包含 1-12 个 argv 参数");
        }
        List<String> normalized = new ArrayList<>();
        for (String item : argv) {
            if (!hasText(item) || item.length() > 160) {
                throw new BusinessException("命令参数不能为空且长度不能超过 160");
            }
            String arg = item.trim();
            if (containsShellMeta(arg)) {
                throw new BusinessException("命令参数包含 shell 元字符或命令拼接语义");
            }
            if (looksLikeExternalTarget(arg)) {
                throw new BusinessException("本机检查策略不允许访问公网 URL 或外部资源");
            }
            normalized.add(arg);
        }
        validateWindowsRegReadonly(normalized);
        String executable = executableName(normalized.getFirst());
        if (FORBIDDEN_EXECUTABLES.contains(executable)) {
            throw new BusinessException("该命令不属于允许的只读本机检查范围");
        }
        return List.copyOf(normalized);
    }

    private void validateWindowsRegReadonly(List<String> argv) {
        if (!"reg".equals(executableName(argv.getFirst()))) {
            return;
        }
        if (argv.size() < 2 || !"query".equalsIgnoreCase(argv.get(1))) {
            throw new BusinessException("Windows reg 只允许 query 只读查询");
        }
    }

    private String executableName(String value) {
        String executable = value.toLowerCase(Locale.ROOT).trim();
        int slash = Math.max(executable.lastIndexOf('/'), executable.lastIndexOf('\\'));
        if (slash >= 0) {
            executable = executable.substring(slash + 1);
        }
        if (executable.endsWith(".exe")) {
            executable = executable.substring(0, executable.length() - 4);
        }
        return executable;
    }

    private boolean containsShellMeta(String value) {
        return value.contains(";")
                || value.contains("|")
                || value.contains("&")
                || value.contains(">")
                || value.contains("<")
                || value.contains("`")
                || value.contains("\n")
                || value.contains("\r")
                || value.contains("$(")
                || value.contains("${");
    }

    private boolean looksLikeExternalTarget(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://");
    }

    private String requireCommandKey(String commandKey) {
        if (!hasText(commandKey) || !commandKey.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BusinessException("commandKey 只能包含字母、数字、下划线和中划线");
        }
        return commandKey.trim();
    }

    public static String normalizeOsType(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (text.contains("win")) {
            return "Windows";
        }
        if (text.contains("mac")) {
            return "macOS";
        }
        return "Linux";
    }

    private String writeArgvJson(List<String> argv) {
        try {
            return objectMapper.writeValueAsString(argv);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("serialize command argv failed", ex);
        }
    }

    private int timeout(Integer timeoutSeconds) {
        return timeoutSeconds == null ? 2 : Math.max(1, Math.min(30, timeoutSeconds));
    }

    private int outputLimitKb(Integer outputLimitKb) {
        return outputLimitKb == null ? 8 : Math.max(1, Math.min(256, outputLimitKb));
    }

    private String statusOrDefault(String status, String fallback) {
        String value = hasText(status) ? status : fallback;
        if (!List.of("draft", "active", "disabled").contains(value)) {
            throw new BusinessException("策略状态必须是 draft、active 或 disabled");
        }
        return value;
    }

    private String severityFor(String category) {
        String value = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (value.contains("network") || value.contains("startup")) {
            return "medium";
        }
        return "low";
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<ResolvedCommand> defaultCommands(String osType) {
        String normalized = normalizeOsType(osType);
        Map<String, List<ResolvedCommand>> catalog = new LinkedHashMap<>();
        catalog.put("Linux", List.of(
                defaultCommand("identity", "检查当前登录身份", "Linux", "identity", "确认当前登录用户和权限组。", List.of("id"), 10, "low"),
                defaultCommand("network", "检查网络连接", "Linux", "network", "查看当前电脑网络连接状态。", List.of("ss", "-tunap"), 20, "medium"),
                defaultCommand("process", "检查正在运行的程序", "Linux", "process", "查看正在运行的程序列表。", List.of("ps", "-axo", "pid,comm"), 30, "medium"),
                defaultCommand("startup", "检查开机启动项", "Linux", "startup", "查看开机启动或用户服务项。", List.of("systemctl", "--user", "list-units", "--type=service", "--no-pager", "--no-legend"), 40, "medium"),
                defaultCommand("hostname", "核对电脑名称", "Linux", "host", "核对电脑名称是否和安全团队记录一致。", List.of("hostname"), 50, "low")
        ));
        catalog.put("macOS", List.of(
                defaultCommand("identity", "检查当前登录身份", "macOS", "identity", "确认当前登录用户和权限组。", List.of("id"), 10, "low"),
                defaultCommand("network", "检查网络连接", "macOS", "network", "查看当前电脑网络连接状态。", List.of("lsof", "-i", "-n", "-P"), 20, "medium"),
                defaultCommand("process", "检查正在运行的程序", "macOS", "process", "查看正在运行的程序列表。", List.of("ps", "-axo", "pid,comm"), 30, "medium"),
                defaultCommand("startup", "检查开机启动项", "macOS", "startup", "查看开机启动或用户服务项。", List.of("launchctl", "list"), 40, "medium"),
                defaultCommand("hostname", "核对电脑名称", "macOS", "host", "核对电脑名称是否和安全团队记录一致。", List.of("hostname"), 50, "low")
        ));
        catalog.put("Windows", List.of(
                defaultCommand("identity", "检查当前登录身份", "Windows", "identity", "确认当前登录用户和权限组。", List.of("whoami", "/groups"), 10, "low"),
                defaultCommand("network", "检查网络连接", "Windows", "network", "查看当前电脑网络连接状态。", List.of("netstat", "-ano"), 20, "medium"),
                defaultCommand("process", "检查正在运行的程序", "Windows", "process", "查看正在运行的程序列表。", List.of("tasklist", "/fo", "table"), 30, "medium"),
                defaultCommand("startup", "检查开机启动项", "Windows", "startup", "查看开机启动或用户服务项。", List.of("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"), 40, "medium"),
                defaultCommand("hostname", "核对电脑名称", "Windows", "host", "核对电脑名称是否和安全团队记录一致。", List.of("hostname"), 50, "low")
        ));
        return catalog.getOrDefault(normalized, catalog.get("Linux"));
    }

    private ResolvedCommand defaultCommand(String commandKey, String displayName, String osType, String category,
                                           String description, List<String> argv, int sortOrder, String severity) {
        return new ResolvedCommand(commandKey, displayName, osType, category, description, argv,
                String.join(" ", argv), 2, 8, severity, sortOrder);
    }

    public record ResolvedCommand(String commandKey, String displayName, String osType, String category,
                                  String description, List<String> argv, String displayCommand,
                                  int timeoutSeconds, int outputLimitKb, String severity, int sortOrder) {
        public ClientCommandOption toClientOption(boolean builtInFallback) {
            return new ClientCommandOption(commandKey, displayName, osType, category, description, displayCommand,
                    phase(category), severity, builtInFallback, sortOrder);
        }

        private String phase(String category) {
            String value = category == null ? "" : category.toLowerCase(Locale.ROOT);
            if (value.contains("identity")) return "identity_observe";
            if (value.contains("network")) return "network_observe";
            if (value.contains("process")) return "process_observe";
            if (value.contains("startup")) return "persistence_observe";
            if (value.contains("host")) return "host_observe";
            return "readonly_observe";
        }
    }

    public record ClientCommandOption(String key, String label, String osType, String category,
                                      String description, String command, String phase,
                                      String severity, boolean builtInFallback, int sortOrder) {
    }

    public record PrecheckResult(boolean passed, String message, List<String> argv) {
    }
}
