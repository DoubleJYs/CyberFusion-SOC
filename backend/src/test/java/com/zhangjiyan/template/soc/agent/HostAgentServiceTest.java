package com.zhangjiyan.template.soc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HostAgentServiceTest {

    @Test
    void heartbeatUsesServerReceiptTimeForOnlineStatus() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        LocalDateTime staleObservedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime beforeHeartbeat = LocalDateTime.now().minusSeconds(1);

        SocHostAgent agent = new SocHostAgent();
        agent.setId(42L);
        agent.setAgentId("macos-real-agent");
        agent.setHostname("mac-host");
        agent.setOsType("macos");
        agent.setStatus("offline");
        agent.setTokenHash("hashed-token");
        agent.setDeleted(0);

        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(agentMapper.updateById(any(SocHostAgent.class))).thenReturn(1);
        when(passwordEncoder.matches(eq("agent-token"), eq("hashed-token"))).thenReturn(true);

        HostAgentService service = new HostAgentService(
                agentMapper,
                mock(SocIngestBatchMapper.class),
                mock(SocIngestRejectLogMapper.class),
                null,
                null,
                null,
                null,
                null,
                passwordEncoder,
                new ObjectMapper()
        );

        HostAgentResponses.AgentHeartbeatResult result = service.heartbeat(
                new HostAgentHeartbeatRequest(
                        "macos-real-agent",
                        "mac-host",
                        "macos",
                        "0.1.0-dev",
                        java.util.List.of("10.0.0.12"),
                        0,
                        0L,
                        4L,
                        4L,
                        0L,
                        120L,
                        staleObservedAt
                ),
                "agent-token",
                "127.0.0.1"
        );

        ArgumentCaptor<SocHostAgent> captor = ArgumentCaptor.forClass(SocHostAgent.class);
        verify(agentMapper).updateById(captor.capture());
        SocHostAgent updated = captor.getValue();

        assertThat(result.status()).isEqualTo("online");
        assertThat(updated.getStatus()).isEqualTo("online");
        assertThat(updated.getLastSeenAt()).isAfterOrEqualTo(beforeHeartbeat);
        assertThat(updated.getLastSeenAt()).isAfter(staleObservedAt.plusHours(23));
        assertThat(updated.getQueueDepth()).isZero();
        assertThat(updated.getSentCount()).isEqualTo(4L);
    }

    @Test
    void ingestEventsUsesAuthenticatedAgentOsForSourceType() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocIngestBatchMapper batchMapper = mock(SocIngestBatchMapper.class);
        SocIngestRejectLogMapper rejectLogMapper = mock(SocIngestRejectLogMapper.class);
        SocExternalEventMapper externalEventMapper = mock(SocExternalEventMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        SocHostAgent agent = macosAgent();
        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(agentMapper.updateById(any(SocHostAgent.class))).thenReturn(1);
        when(passwordEncoder.matches(eq("agent-token"), eq("hashed-token"))).thenReturn(true);
        when(externalEventMapper.selectCount(any())).thenReturn(0L);

        HostAgentService service = new HostAgentService(
                agentMapper,
                batchMapper,
                rejectLogMapper,
                null,
                externalEventMapper,
                null,
                null,
                null,
                passwordEncoder,
                new ObjectMapper()
        );

        service.ingestEvents(new HostEventIngestRequest(
                        "macos-real-agent",
                        "REAL-MAC-EVENT-BATCH",
                        "macos",
                        LocalDateTime.now(),
                        List.of(new HostEventIngestRequest.HostEventPayload(
                                "REAL-MAC-EVENT-0001",
                                "windows-agent",
                                "system_inventory_observed",
                                "info",
                                "HOST-INVENTORY",
                                "Inventory update",
                                null,
                                "10.0.0.12",
                                "mac-host",
                                "10.0.0.12",
                                null,
                                "observed",
                                null,
                                LocalDateTime.now(),
                                Map.of("sourceModule", "windows-agent"),
                                Map.of()
                        ))
                ),
                "agent-token",
                "127.0.0.1"
        );

        ArgumentCaptor<SocExternalEvent> captor = ArgumentCaptor.forClass(SocExternalEvent.class);
        verify(externalEventMapper).insert(captor.capture());
        SocExternalEvent inserted = captor.getValue();

        assertThat(inserted.getSourceType()).isEqualTo("macos-agent");
        assertThat(inserted.getAssetName()).isEqualTo("mac-host");
        assertThat(inserted.getAssetIp()).isEqualTo("10.0.0.12");
    }

    @Test
    void ingestEventsRejectsRegisteredHostOsMismatch() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocIngestRejectLogMapper rejectLogMapper = mock(SocIngestRejectLogMapper.class);
        SocExternalEventMapper externalEventMapper = mock(SocExternalEventMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        SocHostAgent agent = macosAgent();
        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(passwordEncoder.matches(eq("agent-token"), eq("hashed-token"))).thenReturn(true);

        HostAgentService service = new HostAgentService(
                agentMapper,
                mock(SocIngestBatchMapper.class),
                rejectLogMapper,
                null,
                externalEventMapper,
                null,
                null,
                null,
                passwordEncoder,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> service.ingestEvents(new HostEventIngestRequest(
                        "macos-real-agent",
                        "BAD-OS-BATCH",
                        "windows",
                        LocalDateTime.now(),
                        List.of(new HostEventIngestRequest.HostEventPayload(
                                "BAD-OS-EVENT-0001",
                                "eventlog",
                                "system_inventory_observed",
                                "info",
                                "HOST-INVENTORY",
                                "Inventory update",
                                null,
                                "10.0.0.12",
                                "mac-host",
                                "10.0.0.12",
                                null,
                                "observed",
                                null,
                                LocalDateTime.now(),
                                Map.of(),
                                Map.of()
                        ))
                ),
                "agent-token",
                "127.0.0.1"
        )).hasMessageContaining("Agent OS type does not match registered host identity");

        verify(rejectLogMapper).insert(any(SocIngestRejectLog.class));
        verify(externalEventMapper, never()).insert(any(SocExternalEvent.class));
    }

    @Test
    void ingestEventsPromotesRealHostObservationToUnifiedAlert() {
        SocHostAgentMapper agentMapper = mock(SocHostAgentMapper.class);
        SocIngestBatchMapper batchMapper = mock(SocIngestBatchMapper.class);
        SocIngestRejectLogMapper rejectLogMapper = mock(SocIngestRejectLogMapper.class);
        SocExternalEventMapper externalEventMapper = mock(SocExternalEventMapper.class);
        SocAlertMapper alertMapper = mock(SocAlertMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        SocHostAgent agent = macosAgent();
        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(agentMapper.updateById(any(SocHostAgent.class))).thenReturn(1);
        when(passwordEncoder.matches(eq("agent-token"), eq("hashed-token"))).thenReturn(true);
        when(externalEventMapper.selectCount(any())).thenReturn(0L);
        when(alertMapper.selectOne(any())).thenReturn(null);

        HostAgentService service = new HostAgentService(
                agentMapper,
                batchMapper,
                rejectLogMapper,
                null,
                externalEventMapper,
                alertMapper,
                null,
                null,
                passwordEncoder,
                new ObjectMapper()
        );

        service.ingestEvents(new HostEventIngestRequest(
                        "macos-real-agent",
                        "REAL-MAC-CLOSURE-EVENT",
                        "macos",
                        LocalDateTime.now(),
                        List.of(new HostEventIngestRequest.HostEventPayload(
                                "REAL-MAC-LISTEN-0001",
                                "host",
                                "listening_port_observed",
                                "info",
                                "HOST-LISTENING-PORT",
                                "Listening port observed by CyberFusion Agent",
                                null,
                                "10.0.0.12",
                                "mac-host",
                                "10.0.0.12",
                                null,
                                "record",
                                null,
                                LocalDateTime.now(),
                                Map.of("process", "java"),
                                Map.of("agentId", "macos-real-agent")
                        ))
                ),
                "agent-token",
                "127.0.0.1"
        );

        ArgumentCaptor<SocAlert> alertCaptor = ArgumentCaptor.forClass(SocAlert.class);
        verify(alertMapper).insert(alertCaptor.capture());
        SocAlert alert = alertCaptor.getValue();

        assertThat(alert.getSourceType()).isEqualTo("macos-agent");
        assertThat(alert.getEventType()).isEqualTo("listening_port_observed");
        assertThat(alert.getSeverity()).isEqualTo("medium");
        assertThat(alert.getBatchId()).isEqualTo("REAL-MAC-CLOSURE-EVENT");
        assertThat(alert.getRawRef()).isEqualTo("host-agent/REAL-MAC-LISTEN-0001");
    }

    private SocHostAgent macosAgent() {
        SocHostAgent agent = new SocHostAgent();
        agent.setId(42L);
        agent.setAgentId("macos-real-agent");
        agent.setHostname("mac-host");
        agent.setOsType("macos");
        agent.setStatus("online");
        agent.setTokenHash("hashed-token");
        agent.setSentCount(0L);
        agent.setDeleted(0);
        return agent;
    }
}
