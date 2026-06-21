package com.zhangjiyan.template.soc.policy;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCheckPolicyServiceTest {

    @Test
    void returnsActiveCommandsByOsAndFiltersDisabledRows() {
        LocalCheckPolicyService service = serviceWithRows(List.of(
                command("identity", "Linux", 1, "active", "[\"id\"]"),
                command("network", "Linux", 0, "active", "[\"ss\",\"-tunap\"]"),
                command("startup", "Linux", 1, "draft", "[\"systemctl\",\"--user\",\"list-units\"]"),
                command("process", "Windows", 1, "active", "[\"tasklist\",\"/fo\",\"table\"]")
        ));

        List<LocalCheckPolicyService.ClientCommandOption> commands = service.clientCommands("Linux");

        assertThat(commands).extracting(LocalCheckPolicyService.ClientCommandOption::key).containsExactly("identity");
        assertThat(commands.getFirst().builtInFallback()).isFalse();
    }

    @Test
    void rejectsUnknownCommandWhenPoliciesExistForOs() {
        LocalCheckPolicyService service = serviceWithRows(List.of(command("identity", "Linux", 1, "active", "[\"id\"]")));

        assertThatThrownBy(() -> service.resolve("missing", "Linux"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    void rejectsMismatchedOsCommand() {
        LocalCheckPolicyService service = serviceWithRows(List.of(
                command("process", "Linux", 1, "active", "[\"ps\",\"-axo\",\"pid,comm\"]"),
                command("hostname", "Windows", 1, "active", "[\"hostname\"]")
        ));

        assertThatThrownBy(() -> service.resolve("process", "Windows"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    void rejectsShellMetacharactersBeforePublish() {
        LocalCheckPolicyService service = serviceWithRows(List.of());
        LocalCheckCommandRequest request = new LocalCheckCommandRequest(
                "bad", "危险命令", "Linux", "custom_readonly", "bad",
                "[\"id;whoami\"]", 2, 8, true, "draft", 10, "test"
        );

        assertThatThrownBy(() -> service.precheck(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shell");
    }

    @Test
    void rejectsForbiddenExecutablesBeforePublish() {
        LocalCheckPolicyService service = serviceWithRows(List.of());
        LocalCheckCommandRequest request = new LocalCheckCommandRequest(
                "bad", "危险命令", "Linux", "custom_readonly", "bad",
                "[\"bash\",\"-lc\",\"id\"]", 2, 8, true, "draft", 10, "test"
        );

        assertThatThrownBy(() -> service.precheck(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只读本机检查范围");
    }

    @Test
    void rejectsWindowsRegWriteOperations() {
        LocalCheckPolicyService service = serviceWithRows(List.of());
        LocalCheckCommandRequest request = new LocalCheckCommandRequest(
                "startup", "危险注册表操作", "Windows", "startup", "bad",
                "[\"reg\",\"add\",\"HKCU\\\\Software\\\\Demo\"]", 2, 8, true, "draft", 10, "test"
        );

        assertThatThrownBy(() -> service.precheck(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reg 只允许 query");
    }

    @Test
    void resolvesDbArgvFromCommandKeyOnly() {
        LocalCheckPolicyService service = serviceWithRows(List.of(command("network", "Linux", 1, "active", "[\"ss\",\"-tunap\"]")));

        LocalCheckPolicyService.ResolvedCommand command = service.resolve("network", "Linux");

        assertThat(command.argv()).containsExactly("ss", "-tunap");
        assertThat(command.displayCommand()).isEqualTo("ss -tunap");
    }

    @Test
    void fallsBackToBuiltInDefaultsWhenDatabaseHasNoActivePolicies() {
        LocalCheckPolicyService service = serviceWithRows(List.of());

        List<LocalCheckPolicyService.ClientCommandOption> commands = service.clientCommands("Linux");
        LocalCheckPolicyService.ResolvedCommand identity = service.resolve("identity", "Linux");

        assertThat(commands).extracting(LocalCheckPolicyService.ClientCommandOption::key).contains("identity", "network", "process", "startup", "hostname");
        assertThat(commands).allMatch(LocalCheckPolicyService.ClientCommandOption::builtInFallback);
        assertThat(identity.argv()).containsExactly("id");
    }

    @Test
    void fallsBackToBuiltInDefaultsWhenPolicyTableIsUnavailableForClientCommands() {
        LocalCheckPolicyService service = serviceWithMapper(failingMapper());

        List<LocalCheckPolicyService.ClientCommandOption> commands = service.clientCommands("Linux");
        LocalCheckPolicyService.ResolvedCommand hostname = service.resolve("hostname", "Linux");

        assertThat(commands).extracting(LocalCheckPolicyService.ClientCommandOption::key)
                .contains("identity", "network", "process", "startup", "hostname");
        assertThat(commands).allMatch(LocalCheckPolicyService.ClientCommandOption::builtInFallback);
        assertThat(hostname.argv()).containsExactly("hostname");
    }

    @Test
    void localPolicyPageFallsBackToBuiltInRowsWhenPolicyTableIsUnavailable() {
        LocalCheckPolicyService service = serviceWithMapper(failingMapper());

        PageResult<SocLocalCheckCommand> page = service.page(1, 10, "Linux", "active", "网络");

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().getFirst().getCommandKey()).isEqualTo("network");
        assertThat(page.records().getFirst().getSafetyNote()).contains("内置默认策略");
    }

    private static LocalCheckPolicyService serviceWithRows(List<SocLocalCheckCommand> rows) {
        return serviceWithMapper(proxyMapper(rows));
    }

    private static LocalCheckPolicyService serviceWithMapper(SocLocalCheckCommandMapper mapper) {
        SocSecurityScope scope = new SocSecurityScope(null, null, null, null, null) {
            @Override
            public Long currentUserId() {
                return 1L;
            }
        };
        return new LocalCheckPolicyService(mapper, new ObjectMapper(), scope);
    }

    private static SocLocalCheckCommand command(String key, String osType, int enabled, String status, String argvJson) {
        SocLocalCheckCommand command = new SocLocalCheckCommand();
        command.setId((long) Math.abs((key + osType + status).hashCode()));
        command.setCommandKey(key);
        command.setDisplayName(key);
        command.setOsType(osType);
        command.setCategory(key);
        command.setDescription(key);
        command.setCommandArgvJson(argvJson);
        command.setTimeoutSeconds(2);
        command.setOutputLimitKb(8);
        command.setEnabled(enabled);
        command.setStatus(status);
        command.setVersion(1);
        command.setSortOrder(10);
        command.setDeleted(0);
        return command;
    }

    @SuppressWarnings("unchecked")
    private static SocLocalCheckCommandMapper proxyMapper(List<SocLocalCheckCommand> rows) {
        AtomicReference<SocLocalCheckCommand> selectedById = new AtomicReference<>();
        return (SocLocalCheckCommandMapper) Proxy.newProxyInstance(
                LocalCheckPolicyServiceTest.class.getClassLoader(),
                new Class[]{SocLocalCheckCommandMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "selectList" -> rows;
                    case "selectPage" -> {
                        Page<SocLocalCheckCommand> page = (Page<SocLocalCheckCommand>) args[0];
                        page.setRecords(rows);
                        page.setTotal(rows.size());
                        yield page;
                    }
                    case "selectById" -> rows.stream()
                            .filter(row -> row.getId().equals(((Number) args[0]).longValue()))
                            .findFirst()
                            .orElse(selectedById.get());
                    case "insert", "updateById" -> 1;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static SocLocalCheckCommandMapper failingMapper() {
        return (SocLocalCheckCommandMapper) Proxy.newProxyInstance(
                LocalCheckPolicyServiceTest.class.getClassLoader(),
                new Class[]{SocLocalCheckCommandMapper.class},
                (_proxy, method, _args) -> switch (method.getName()) {
                    case "selectList", "selectPage" -> throw new RuntimeException("table unavailable");
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
