package com.zhangjiyan.template.soc.agent;

import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRuntimeResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HostAgentRuntimeServiceTest {

    @Test
    void startEnablesAgentBeforeRunningLocalCommand() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocHostAgent agent = macosAgent("macos-host-agent");
        agent.setEnabled(0);
        agent.setStatus("disabled");
        SocHostAgent fresh = macosAgent("macos-host-agent");
        fresh.setLastSeenAt(LocalDateTime.now().plusSeconds(1));

        when(agentMapper.selectById(42L)).thenReturn(agent, fresh);
        when(agentMapper.updateById(any(SocHostAgent.class))).thenReturn(1);
        RecordingRuntimeService service = new RecordingRuntimeService(agentMapper);

        AgentRuntimeResult result = service.control(42L, new HostAgentRuntimeRequest("start"));

        ArgumentCaptor<SocHostAgent> captor = ArgumentCaptor.forClass(SocHostAgent.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo("offline");
        assertThat(service.actions).containsExactly("start:macos-host-agent");
        assertThat(result.enabled()).isTrue();
        assertThat(result.runtimeStatus()).isEqualTo("online");
    }

    @Test
    void stopRunsLocalCommandAndDisablesAgent() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocHostAgent agent = macosAgent("macos-host-agent");
        when(agentMapper.selectById(42L)).thenReturn(agent);
        when(agentMapper.updateById(any(SocHostAgent.class))).thenReturn(1);
        RecordingRuntimeService service = new RecordingRuntimeService(agentMapper);

        AgentRuntimeResult result = service.control(42L, new HostAgentRuntimeRequest("stop"));

        ArgumentCaptor<SocHostAgent> captor = ArgumentCaptor.forClass(SocHostAgent.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isZero();
        assertThat(captor.getValue().getStatus()).isEqualTo("disabled");
        assertThat(service.actions).containsExactly("stop:macos-host-agent");
        assertThat(result.enabled()).isFalse();
        assertThat(result.commandExecuted()).isTrue();
    }

    @Test
    void stopStillDisablesAcceptanceWhenLocalCommandFails() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocHostAgent agent = macosAgent("macos-host-agent");
        when(agentMapper.selectById(42L)).thenReturn(agent);
        when(agentMapper.updateById(any(SocHostAgent.class))).thenReturn(1);
        RecordingRuntimeService service = new RecordingRuntimeService(agentMapper);
        service.failCommand = true;

        AgentRuntimeResult result = service.control(42L, new HostAgentRuntimeRequest("stop"));

        ArgumentCaptor<SocHostAgent> captor = ArgumentCaptor.forClass(SocHostAgent.class);
        verify(agentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isZero();
        assertThat(result.commandExecuted()).isFalse();
        assertThat(result.message()).contains("平台已关闭接收");
    }

    @Test
    void unsafeAgentIdIsRejectedBeforeCommandExecution() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocHostAgent agent = macosAgent("bad;agent");
        when(agentMapper.selectById(42L)).thenReturn(agent);
        RecordingRuntimeService service = new RecordingRuntimeService(agentMapper);

        assertThatThrownBy(() -> service.control(42L, new HostAgentRuntimeRequest("start")))
                .hasMessageContaining("非法字符");

        assertThat(service.actions).isEmpty();
        verify(agentMapper, never()).updateById(any(SocHostAgent.class));
    }

    @Test
    void startRejectsMissingLocalRuntimeBeforeChangingAcceptance() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocHostAgent agent = macosAgent("old-validation-agent");
        when(agentMapper.selectById(42L)).thenReturn(agent);
        RecordingRuntimeService service = new RecordingRuntimeService(agentMapper);
        service.runtimeInstalled = false;

        assertThatThrownBy(() -> service.control(42L, new HostAgentRuntimeRequest("start")))
                .hasMessageContaining("本机未安装");

        assertThat(service.actions).isEmpty();
        verify(agentMapper, never()).updateById(any(SocHostAgent.class));
    }

    @Test
    void runtimeCommandReceivesSameEnvironmentRootAsInstallInspector() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocHostAgent agent = macosAgent("macos-host-agent");
        RecordingRuntimeService service = new RecordingRuntimeService(agentMapper);

        Map<String, String> env = service.preparedEnv(agent);

        assertThat(env).containsEntry("CYBERFUSION_AGENT_ID", "macos-host-agent");
        assertThat(env.get("CYBERFUSION_ENV_ROOT"))
                .isEqualTo(HostAgentRuntimeInspector.envRoot().toString());
    }

    private SocHostAgent macosAgent(String agentId) {
        SocHostAgent agent = new SocHostAgent();
        agent.setId(42L);
        agent.setAgentId(agentId);
        agent.setAgentName("Mac Agent");
        agent.setHostname("mac-host");
        agent.setOsType("macos");
        agent.setStatus("online");
        agent.setEnabled(1);
        agent.setDeleted(0);
        return agent;
    }

    private static class RecordingRuntimeService extends HostAgentRuntimeService {
        private final List<String> actions = new ArrayList<>();
        private boolean failCommand;
        private boolean runtimeInstalled = true;

        RecordingRuntimeService(SocHostAgentMapper agentMapper) {
            super(agentMapper);
        }

        @Override
        protected CommandOutcome executeRuntimeCommand(SocHostAgent agent, String action) {
            actions.add(action + ":" + agent.getAgentId());
            if (failCommand) {
                throw new BusinessException("missing local runtime");
            }
            return new CommandOutcome(0, "ok");
        }

        @Override
        protected String localOsType() {
            return "macos";
        }

        @Override
        protected HostAgentRuntimeInspector.RuntimeInstallStatus runtimeInstallStatus(SocHostAgent agent) {
            if (!runtimeInstalled) {
                return new HostAgentRuntimeInspector.RuntimeInstallStatus(false, "missing_runtime", "本机未安装该 Agent 运行时，仅保留历史上报记录。");
            }
            return new HostAgentRuntimeInspector.RuntimeInstallStatus(true, "installed", "本机运行时已安装，可以通过页面启停。");
        }

        Map<String, String> preparedEnv(SocHostAgent agent) {
            Map<String, String> env = new HashMap<>();
            prepareRuntimeEnvironment(env, agent);
            return env;
        }
    }
}
