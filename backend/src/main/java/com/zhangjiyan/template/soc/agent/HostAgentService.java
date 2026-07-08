package com.zhangjiyan.template.soc.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentBatchItem;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentDetail;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentHeartbeatResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentOverview;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRegistrationResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentRejectItem;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.AgentSourceHealth;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.IngestResult;
import com.zhangjiyan.template.soc.agent.HostAgentResponses.RecentHostEvent;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheck;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEvent;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HostAgentService {

    public static final String AGENT_TOKEN_HEADER = "X-CyberFusion-Agent-Token";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long AGENT_ONLINE_WINDOW_MINUTES = 10;
    private static final List<String> AGENT_SOURCE_TYPES = List.of("macos-agent", "windows-agent", "host-agent");
    private static final List<String> ALERT_EVENT_TYPES = List.of(
            "windows_logon_failure",
            "failed_logon",
            "login_failure",
            "new_service_installed",
            "defender_detection",
            "suspicious_powershell",
            "sensitive_file_hash_changed",
            "new_listening_port",
            "listening_port_opened",
            "baseline_failed"
    );
    private static final List<String> HOST_OBSERVATION_ALERT_TYPES = List.of(
            "listening_port_observed",
            "process_summary_observed",
            "macos_startup_items_observed",
            "macos_system_log_summary_observed",
            "windows_logon_activity",
            "windows_system_service_activity",
            "windows_application_error_activity",
            "windows_powershell_operational_activity",
            "windows_defender_detection_activity",
            "windows_sysmon_activity",
            "windows_service_summary_observed",
            "windows_listening_port_summary_observed",
            "windows_startup_summary_observed"
    );
    private static final List<SourceGroup> SOURCE_GROUPS = List.of(
            new SourceGroup("macos-agent", "macOS Agent", List.of("macos"), List.of("macos-agent"), false),
            new SourceGroup("windows-agent", "Windows Agent", List.of("windows"), List.of("windows-agent"), false),
            new SourceGroup("host-agent", "Other Host Agent", List.of("linux", "unknown"), List.of("host-agent"), false),
            new SourceGroup("wazuh", "Wazuh", List.of(), List.of("wazuh", "wazuh-api", "wazuh-indexer"), false),
            new SourceGroup("demo", "Demo", List.of(), List.of("demo"), true)
    );

    private final SocHostAgentMapper agentMapper;
    private final SocIngestBatchMapper batchMapper;
    private final SocIngestRejectLogMapper rejectLogMapper;
    private final SocAssetMapper assetMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocAlertMapper alertMapper;
    private final SocFileIntegrityEventMapper fimMapper;
    private final SocBaselineCheckMapper baselineMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public List<SocHostAgent> listAgents(String osType, String status) {
        LocalDateTime now = LocalDateTime.now();
        List<SocHostAgent> agents = agentMapper.selectList(new LambdaQueryWrapper<SocHostAgent>()
                .eq(SocHostAgent::getDeleted, 0)
                .eq(osType != null && !osType.isBlank(), SocHostAgent::getOsType, normalizeOs(osType))
                .orderByDesc(SocHostAgent::getLastSeenAt));
        return agents.stream()
                .map(agent -> sanitizeAgent(agent, now))
                .filter(agent -> !notBlank(status) || status.equalsIgnoreCase(firstNotBlank(agent.getStatus(), "")))
                .toList();
    }

    public AgentOverview overview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);
        List<SocHostAgent> agents = listAgents(null, null);
        long onlineAgents = agents.stream().filter(agent -> "online".equals(agent.getStatus())).count();
        List<AgentSourceHealth> sources = SOURCE_GROUPS.stream()
                .map(group -> sourceHealth(group, agents, since))
                .toList();
        return new AgentOverview(
                agents.size(),
                onlineAgents,
                agents.size() - onlineAgents,
                agents.stream().filter(agent -> "macos".equals(normalizeOs(agent.getOsType()))).count(),
                agents.stream().filter(agent -> "windows".equals(normalizeOs(agent.getOsType()))).count(),
                agents.stream().filter(agent -> "linux".equals(normalizeOs(agent.getOsType()))).count(),
                countAssets(AGENT_SOURCE_TYPES),
                countExternalEvents(AGENT_SOURCE_TYPES, since, false),
                countFimEvents(AGENT_SOURCE_TYPES, since),
                countFailedBaselines(AGENT_SOURCE_TYPES),
                countBatches(since, null),
                countRejects(since, null),
                sources,
                agents,
                recentEventsForSources(AGENT_SOURCE_TYPES, 10)
        );
    }

    public AgentDetail detail(Long id) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);
        SocHostAgent agent = agentMapper.selectById(id);
        if (agent == null || Integer.valueOf(1).equals(agent.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Host Agent not found");
        }
        SocHostAgent sanitizedAgent = sanitizeAgent(agent, now);
        SourceGroup group = SOURCE_GROUPS.stream()
                .filter(item -> item.sourceType().equals(agentSourceType(sanitizedAgent)))
                .findFirst()
                .orElse(new SourceGroup(agentSourceType(sanitizedAgent), "Host Agent", List.of(normalizeOs(sanitizedAgent.getOsType())), List.of(agentSourceType(sanitizedAgent)), false));
        List<AgentBatchItem> batches = batchMapper.selectList(new LambdaQueryWrapper<SocIngestBatch>()
                        .and(wrapper -> wrapper.eq(SocIngestBatch::getAgentDbId, sanitizedAgent.getId())
                                .or()
                                .eq(SocIngestBatch::getAgentId, sanitizedAgent.getAgentId()))
                        .orderByDesc(SocIngestBatch::getFinishedAt)
                        .last("LIMIT 12"))
                .stream()
                .map(this::toBatchItem)
                .toList();
        List<String> batchIds = batches.stream().map(AgentBatchItem::batchId).filter(HostAgentService::notBlank).toList();
        return new AgentDetail(
                sanitizedAgent,
                sourceHealth(group, List.of(sanitizedAgent), since),
                batches,
                recentEventsForAgent(sanitizedAgent, batchIds),
                rejectLogMapper.selectList(new LambdaQueryWrapper<SocIngestRejectLog>()
                                .eq(SocIngestRejectLog::getAgentId, sanitizedAgent.getAgentId())
                                .orderByDesc(SocIngestRejectLog::getCreatedAt)
                                .last("LIMIT 10"))
                        .stream()
                        .map(this::toRejectItem)
                        .toList()
        );
    }

    @Transactional
    public AgentRegistrationResult register(HostAgentRegisterRequest request, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        String agentId = firstNotBlank(request.agentId(), defaultAgentId(request.osType(), request.hostname()));
        SocHostAgent agent = findAgent(agentId);
        boolean created = agent == null;
        if (agent == null) {
            agent = new SocHostAgent();
            agent.setAgentId(agentId);
            agent.setFirstSeenAt(now);
            agent.setDeleted(0);
            agent.setOwnerId(SecurityUtils.currentUser().map(user -> user.userId()).orElse(null));
        }
        String token = newToken();
        agent.setAgentName(firstNotBlank(request.agentName(), request.hostname()));
        agent.setHostname(request.hostname());
        agent.setOsType(normalizeOs(request.osType()));
        agent.setOsVersion(request.osVersion());
        agent.setArchitecture(request.architecture());
        agent.setAgentVersion(request.agentVersion());
        agent.setIpAddressesJson(writeJson(request.ipAddresses()));
        agent.setMacAddressesJson(writeJson(request.macAddresses()));
        agent.setLabelsJson(writeJson(request.labels()));
        agent.setTokenHash(passwordEncoder.encode(token));
        agent.setStatus("online");
        agent.setLastIp(clientIp);
        agent.setLastSeenAt(now);
        agent.setQueueDepth(0);
        agent.setQueueBytes(0L);
        agent.setCollectedCount(0L);
        agent.setSentCount(0L);
        agent.setFailedCount(0L);
        if (created) {
            agentMapper.insert(agent);
        } else {
            agentMapper.updateById(agent);
        }
        return new AgentRegistrationResult(agentId, token, "online", now,
                created ? "Agent registered. Store the token in the local host agent config." : "Agent token rotated. Update the local host agent config.");
    }

    @Transactional
    public AgentHeartbeatResult heartbeat(HostAgentHeartbeatRequest request, String token, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        SocHostAgent agent = authenticate(request.agentId(), token, "heartbeat", null, null);
        ensureAgentOsCompatible(agent, request.osType(), "heartbeat", null);
        if (notBlank(request.hostname())) {
            agent.setHostname(request.hostname());
            agent.setAgentName(firstNotBlank(agent.getAgentName(), request.hostname()));
        }
        if (notBlank(request.osType())) {
            agent.setOsType(normalizeOs(request.osType()));
        }
        if (notBlank(request.agentVersion())) {
            agent.setAgentVersion(request.agentVersion());
        }
        agent.setIpAddressesJson(writeJson(request.ipAddresses()));
        agent.setStatus("online");
        agent.setLastIp(clientIp);
        // Online/offline state must reflect when the platform received the heartbeat.
        // Collector timestamps stay on asset/event/FIM/baseline payloads.
        agent.setLastSeenAt(now);
        agent.setQueueDepth(nz(request.queueDepth()));
        agent.setQueueBytes(nz(request.queueBytes()));
        agent.setCollectedCount(nz(request.collectedCount()));
        agent.setSentCount(nz(request.sentCount()));
        agent.setFailedCount(nz(request.failedCount()));
        agentMapper.updateById(agent);
        return new AgentHeartbeatResult(agent.getAgentId(), "online", now, "Heartbeat accepted.");
    }

    @Transactional
    public IngestResult ingestAssets(HostAssetIngestRequest request, String token, String clientIp) {
        SocHostAgent agent = authenticate(request.agentId(), token, "asset", request.batchId(), null);
        ensureAgentOsCompatible(agent, request.osType(), "asset", request.batchId());
        String batchId = batchId(request.agentId(), request.batchId(), "asset");
        LocalDateTime now = LocalDateTime.now();
        int accepted = 0;
        for (HostAssetIngestRequest.HostAssetPayload item : request.assets()) {
            upsertAsset(agent, item, firstNonNull(item.observedAt(), request.collectedAt(), now));
            accepted++;
        }
        finishAgentIngest(agent, request.osType(), clientIp, accepted);
        writeBatch(agent, batchId, "asset", request.osType(), request.assets().size(), accepted, 0, 0, "accepted", null);
        return new IngestResult(batchId, agent.getAgentId(), "asset", accepted, 0, 0, "Assets accepted.");
    }

    @Transactional
    public IngestResult ingestEvents(HostEventIngestRequest request, String token, String clientIp) {
        SocHostAgent agent = authenticate(request.agentId(), token, "event", request.batchId(), null);
        ensureAgentOsCompatible(agent, request.osType(), "event", request.batchId());
        String batchId = batchId(request.agentId(), request.batchId(), "event");
        int accepted = 0;
        int duplicates = 0;
        LocalDateTime now = LocalDateTime.now();
        for (HostEventIngestRequest.HostEventPayload item : request.events()) {
            if (externalEventExists(item.eventUid())) {
                duplicates++;
                continue;
            }
            SocExternalEvent event = new SocExternalEvent();
            event.setEventUid(item.eventUid());
            event.setSourceType(sourceType(agent, item.sourceModule()));
            event.setEventType(item.eventType());
            event.setSeverity(item.severity());
            event.setRuleId(item.ruleId());
            event.setRuleName(firstNotBlank(item.ruleName(), item.eventType()));
            event.setSrcIp(item.srcIp());
            event.setDestIp(item.destIp());
            event.setAssetName(firstNotBlank(item.assetName(), agent.getHostname()));
            event.setAssetIp(firstNotBlank(item.assetIp(), item.destIp(), primaryIp(agent.getIpAddressesJson())));
            event.setBatchId(batchId);
            event.setDemoCaseId(null);
            event.setTargetUrl(item.targetUrl());
            event.setAction(item.action());
            event.setRequestId(batchId);
            event.setCorrelationKey(correlationKey(batchId, event.getAssetIp(), item.ruleId(), item.eventType()));
            event.setIoc(item.ioc());
            event.setRawEvent(writeJson(firstNonNull(item.raw(), Map.of())));
            event.setNormalizedEvent(writeJson(firstNonNull(item.normalized(), Map.of())));
            event.setStatus("new");
            event.setOwnerId(agent.getOwnerId());
            event.setDeptId(agent.getDeptId());
            event.setEventTime(firstNonNull(item.eventTime(), request.collectedAt(), now));
            event.setDeleted(0);
            externalEventMapper.insert(event);
            SocAlert alert = upsertHostAlert(agent, event);
            if (alert != null) {
                event.setAlertId(alert.getId());
                event.setStatus("linked");
                externalEventMapper.updateById(event);
            }
            accepted++;
        }
        finishAgentIngest(agent, request.osType(), clientIp, accepted);
        writeBatch(agent, batchId, "event", request.osType(), request.events().size(), accepted, duplicates, 0, "accepted", null);
        return new IngestResult(batchId, agent.getAgentId(), "event", accepted, duplicates, 0, "Host events accepted.");
    }

    @Transactional
    public IngestResult ingestFim(HostFimIngestRequest request, String token, String clientIp) {
        SocHostAgent agent = authenticate(request.agentId(), token, "fim", request.batchId(), null);
        ensureAgentOsCompatible(agent, request.osType(), "fim", request.batchId());
        String batchId = batchId(request.agentId(), request.batchId(), "fim");
        int accepted = 0;
        int duplicates = 0;
        LocalDateTime now = LocalDateTime.now();
        for (HostFimIngestRequest.HostFimPayload item : request.events()) {
            if (fimExists(item.eventUid())) {
                duplicates++;
                continue;
            }
            SocFileIntegrityEvent event = new SocFileIntegrityEvent();
            event.setEventUid(item.eventUid());
            event.setAction(item.action());
            event.setSeverity(item.severity());
            event.setHostname(item.hostname());
            event.setAssetIp(item.assetIp());
            event.setFilePath(item.filePath());
            event.setRuleName(firstNotBlank(item.ruleName(), "Host FIM " + item.action()));
            event.setStatus("new");
            event.setSourceType(agentSourceType(agent));
            event.setOwnerId(agent.getOwnerId());
            event.setDeptId(agent.getDeptId());
            event.setEventTime(firstNonNull(item.eventTime(), request.collectedAt(), now));
            event.setDeleted(0);
            fimMapper.insert(event);
            accepted++;
        }
        finishAgentIngest(agent, request.osType(), clientIp, accepted);
        writeBatch(agent, batchId, "fim", request.osType(), request.events().size(), accepted, duplicates, 0, "accepted", null);
        return new IngestResult(batchId, agent.getAgentId(), "fim", accepted, duplicates, 0, "FIM events accepted.");
    }

    @Transactional
    public IngestResult ingestBaseline(HostBaselineIngestRequest request, String token, String clientIp) {
        SocHostAgent agent = authenticate(request.agentId(), token, "baseline", request.batchId(), null);
        ensureAgentOsCompatible(agent, request.osType(), "baseline", request.batchId());
        String batchId = batchId(request.agentId(), request.batchId(), "baseline");
        int accepted = 0;
        LocalDateTime now = LocalDateTime.now();
        for (HostBaselineIngestRequest.HostBaselinePayload item : request.checks()) {
            SocBaselineCheck check = baselineMapper.selectOne(new LambdaQueryWrapper<SocBaselineCheck>()
                    .eq(SocBaselineCheck::getCheckCode, item.checkCode())
                    .eq(SocBaselineCheck::getAssetIp, item.assetIp())
                    .last("LIMIT 1"));
            boolean created = check == null;
            if (check == null) {
                check = new SocBaselineCheck();
                check.setCheckCode(item.checkCode());
                check.setAssetIp(item.assetIp());
                check.setDeleted(0);
            }
            check.setCategory(item.category());
            check.setCheckItem(item.checkItem());
            check.setAssetName(item.assetName());
            check.setResult(item.result());
            check.setSeverity(item.severity());
            check.setPassRate(firstNonNull(item.passRate(), "pass".equals(item.result()) ? 100 : 0));
            check.setRemediation(firstNotBlank(item.remediation(), "Review host baseline evidence from " + agent.getAgentId() + "."));
            check.setStatus(firstNotBlank(item.status(), "pass".equals(item.result()) ? "passed" : "failed"));
            check.setSourceType(agentSourceType(agent));
            check.setOwnerId(agent.getOwnerId());
            check.setDeptId(agent.getDeptId());
            check.setCheckedAt(firstNonNull(item.checkedAt(), request.collectedAt(), now));
            if (created) {
                baselineMapper.insert(check);
            } else {
                baselineMapper.updateById(check);
            }
            accepted++;
        }
        finishAgentIngest(agent, request.osType(), clientIp, accepted);
        writeBatch(agent, batchId, "baseline", request.osType(), request.checks().size(), accepted, 0, 0, "accepted", null);
        return new IngestResult(batchId, agent.getAgentId(), "baseline", accepted, 0, 0, "Baseline checks accepted.");
    }

    private SocHostAgent authenticate(String agentId, String token, String ingestType, String batchId, Object payload) {
        if (!notBlank(agentId) || !notBlank(token)) {
            writeReject(batchId, agentId, null, ingestType, "agent_auth_failed", "Agent id and token are required.", payload);
            throw new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "Agent id or token is missing");
        }
        SocHostAgent agent = findAgent(agentId);
        if (agent == null || Integer.valueOf(1).equals(agent.getDeleted()) || !passwordEncoder.matches(token, agent.getTokenHash())) {
            writeReject(batchId, agentId, null, ingestType, "agent_auth_failed", "Agent token rejected.", payload);
            throw new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "Agent token is invalid");
        }
        return agent;
    }

    private void upsertAsset(SocHostAgent agent, HostAssetIngestRequest.HostAssetPayload item, LocalDateTime observedAt) {
        SocAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, item.primaryIp())
                .last("LIMIT 1"));
        boolean created = asset == null;
        if (asset == null) {
            asset = new SocAsset();
            asset.setIp(item.primaryIp());
            asset.setRiskScore(0);
            asset.setRiskLevel("low");
            asset.setOpenAlertCount(0);
            asset.setDeleted(0);
        }
        asset.setHostname(item.hostname());
        asset.setOsType(firstNotBlank(item.osType(), agent.getOsType(), "unknown"));
        asset.setSourceType(agentSourceType(agent));
        asset.setOwnerId(agent.getOwnerId());
        asset.setDeptId(agent.getDeptId());
        asset.setOwnerName(item.ownerName());
        asset.setDeptName(item.deptName());
        asset.setLastSeenAt(observedAt);
        if (created) {
            assetMapper.insert(asset);
        } else {
            assetMapper.updateById(asset);
        }
    }

    private SocAlert upsertHostAlert(SocHostAgent agent, SocExternalEvent event) {
        if (!shouldCreateHostAlert(event)) {
            return null;
        }
        String alertUid = hostAlertUid(event.getEventUid());
        SocAlert alert = alertMapper.selectOne(new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getAlertUid, alertUid)
                .last("LIMIT 1"));
        boolean created = alert == null;
        if (alert == null) {
            alert = new SocAlert();
            alert.setAlertUid(alertUid);
            alert.setStatus("new");
        }
        String alertSeverity = alertSeverityFor(event);
        alert.setSourceType(event.getSourceType());
        alert.setLevel(levelOf(alertSeverity));
        alert.setSeverity(alertSeverity);
        alert.setRuleId(firstNotBlank(event.getRuleId(), event.getEventType(), "HOST-AGENT"));
        alert.setRuleDescription(firstNotBlank(event.getRuleName(), event.getEventType(), "Host Agent event"));
        alert.setAssetName(firstNotBlank(event.getAssetName(), agent.getHostname(), event.getAssetIp(), "host-agent-asset"));
        alert.setAssetIp(firstNotBlank(event.getAssetIp(), event.getDestIp(), primaryIp(agent.getIpAddressesJson()), "0.0.0.0"));
        alert.setSourceIp(event.getSrcIp());
        alert.setEventType(event.getEventType());
        alert.setTargetUrl(event.getTargetUrl());
        alert.setAction(event.getAction());
        alert.setEvidenceSummary(hostAlertSummary(event));
        alert.setBatchId(event.getBatchId());
        alert.setDemoCaseId(event.getDemoCaseId());
        alert.setCorrelationKey(event.getCorrelationKey());
        alert.setTactic(tacticOf(event.getEventType()));
        alert.setRawRef("host-agent/" + event.getEventUid());
        alert.setEventTime(event.getEventTime());
        alert.setOwnerId(event.getOwnerId());
        alert.setDeptId(event.getDeptId());
        alert.setDeleted(0);
        if (created) {
            alertMapper.insert(alert);
        } else {
            alertMapper.updateById(alert);
        }
        return alert;
    }

    private boolean shouldCreateHostAlert(SocExternalEvent event) {
        if (severityWeight(event.getSeverity()) >= severityWeight("high")) {
            return true;
        }
        String eventType = String.valueOf(event.getEventType()).toLowerCase(Locale.ROOT);
        return ALERT_EVENT_TYPES.stream().anyMatch(eventType::contains)
                || HOST_OBSERVATION_ALERT_TYPES.stream().anyMatch(eventType::contains);
    }

    private String alertSeverityFor(SocExternalEvent event) {
        String severity = firstNotBlank(event.getSeverity(), "low");
        String eventType = String.valueOf(event.getEventType()).toLowerCase(Locale.ROOT);
        if (HOST_OBSERVATION_ALERT_TYPES.stream().anyMatch(eventType::contains)
                && severityWeight(severity) < severityWeight("medium")) {
            return "medium";
        }
        return severity;
    }

    private String hostAlertUid(String eventUid) {
        return "HOST-ALERT-" + java.util.UUID.nameUUIDFromBytes(String.valueOf(eventUid).getBytes(StandardCharsets.UTF_8));
    }

    private String hostAlertSummary(SocExternalEvent event) {
        return "Host Agent event " + firstNotBlank(event.getEventType(), "unknown")
                + " from " + firstNotBlank(event.getSourceType(), "host-agent")
                + " on " + firstNotBlank(event.getAssetName(), event.getAssetIp(), "unknown asset");
    }

    private int levelOf(String severity) {
        return switch (String.valueOf(severity).toLowerCase(Locale.ROOT)) {
            case "critical" -> 15;
            case "high" -> 12;
            case "medium" -> 8;
            default -> 3;
        };
    }

    private int severityWeight(String severity) {
        return switch (String.valueOf(severity).toLowerCase(Locale.ROOT)) {
            case "critical" -> 90;
            case "high" -> 70;
            case "medium" -> 50;
            case "low" -> 30;
            default -> 10;
        };
    }

    private String tacticOf(String eventType) {
        String normalized = String.valueOf(eventType).toLowerCase(Locale.ROOT);
        if (normalized.contains("logon") || normalized.contains("login")) {
            return "Credential Access";
        }
        if (normalized.contains("powershell")) {
            return "Execution";
        }
        if (normalized.contains("service") || normalized.contains("startup")) {
            return "Persistence";
        }
        if (normalized.contains("defender") || normalized.contains("malware")) {
            return "Malware Defense";
        }
        if (normalized.contains("port") || normalized.contains("network")) {
            return "Discovery";
        }
        return "Host Security";
    }

    private void finishAgentIngest(SocHostAgent agent, String osType, String clientIp, int accepted) {
        agent.setStatus("online");
        agent.setLastIp(clientIp);
        agent.setLastSeenAt(LocalDateTime.now());
        if (notBlank(osType)) {
            agent.setOsType(normalizeOs(osType));
        }
        agent.setSentCount(nz(agent.getSentCount()) + accepted);
        agentMapper.updateById(agent);
    }

    private void writeBatch(SocHostAgent agent, String batchId, String ingestType, String osType, int total, int accepted,
                            int duplicates, int rejected, String status, String errorMessage) {
        SocIngestBatch existing = batchMapper.selectOne(new LambdaQueryWrapper<SocIngestBatch>()
                .eq(SocIngestBatch::getBatchId, batchId)
                .last("LIMIT 1"));
        SocIngestBatch batch = existing == null ? new SocIngestBatch() : existing;
        batch.setBatchId(batchId);
        batch.setAgentId(agent.getAgentId());
        batch.setAgentDbId(agent.getId());
        batch.setSourceOs(firstNotBlank(osType, agent.getOsType()));
        batch.setIngestType(ingestType);
        batch.setItemCount(total);
        batch.setAcceptedCount(accepted);
        batch.setDuplicateCount(duplicates);
        batch.setRejectedCount(rejected);
        batch.setStatus(status);
        batch.setStartedAt(firstNonNull(batch.getStartedAt(), LocalDateTime.now()));
        batch.setFinishedAt(LocalDateTime.now());
        batch.setErrorMessage(errorMessage);
        if (existing == null) {
            batchMapper.insert(batch);
        } else {
            batchMapper.updateById(batch);
        }
    }

    private void writeReject(String batchId, String agentId, String eventUid, String ingestType, String reasonCode,
                             String reason, Object payload) {
        SocIngestRejectLog rejectLog = new SocIngestRejectLog();
        rejectLog.setBatchId(batchId);
        rejectLog.setAgentId(agentId);
        rejectLog.setEventUid(eventUid);
        rejectLog.setIngestType(ingestType);
        rejectLog.setReasonCode(reasonCode);
        rejectLog.setReason(reason);
        rejectLog.setPayloadJson(writeJson(payload));
        rejectLog.setCreatedAt(LocalDateTime.now());
        rejectLogMapper.insert(rejectLog);
    }

    private void ensureAgentOsCompatible(SocHostAgent agent, String reportedOsType, String ingestType, String batchId) {
        if (!notBlank(reportedOsType)) {
            return;
        }
        String existingOs = normalizeOs(agent.getOsType());
        String reportedOs = normalizeOs(reportedOsType);
        if (!notBlank(existingOs) || "unknown".equals(existingOs) || existingOs.equals(reportedOs)) {
            return;
        }
        writeReject(batchId, agent.getAgentId(), null, ingestType, "agent_os_mismatch",
                "Agent OS type does not match registered host identity.",
                Map.of("registeredOsType", existingOs, "reportedOsType", reportedOs));
        throw new BusinessException(ResultCode.BAD_REQUEST, "Agent OS type does not match registered host identity");
    }

    private SocHostAgent findAgent(String agentId) {
        if (!notBlank(agentId)) {
            return null;
        }
        return agentMapper.selectOne(new LambdaQueryWrapper<SocHostAgent>()
                .eq(SocHostAgent::getAgentId, agentId)
                .eq(SocHostAgent::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private boolean externalEventExists(String eventUid) {
        return externalEventMapper.selectCount(new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getEventUid, eventUid)) > 0;
    }

    private boolean fimExists(String eventUid) {
        return fimMapper.selectCount(new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getEventUid, eventUid)) > 0;
    }

    private AgentSourceHealth sourceHealth(SourceGroup group, List<SocHostAgent> agents, LocalDateTime since) {
        long agentCount = agents.stream()
                .filter(agent -> group.osTypes().contains(normalizeOs(agent.getOsType()))
                        || group.sourceType().equals(agentSourceType(agent)))
                .count();
        long onlineCount = agents.stream()
                .filter(agent -> "online".equals(agent.getStatus()))
                .filter(agent -> group.osTypes().contains(normalizeOs(agent.getOsType()))
                        || group.sourceType().equals(agentSourceType(agent)))
                .count();
        long assetCount = group.demo()
                ? assetMapper.selectCount(new LambdaQueryWrapper<SocAsset>().eq(SocAsset::getDeleted, 0).eq(SocAsset::getSourceType, "demo"))
                : countAssets(group.eventSourceTypes());
        long eventCount = countExternalEvents(group.eventSourceTypes(), since, group.demo());
        long fimCount = group.demo() ? 0 : countFimEvents(group.eventSourceTypes(), since);
        long failedBaselineCount = group.demo() ? 0 : countFailedBaselines(group.eventSourceTypes());
        String status = onlineCount > 0 ? "online" : (agentCount + assetCount + eventCount + fimCount > 0 ? "warning" : "empty");
        return new AgentSourceHealth(group.sourceType(), group.label(), agentCount, onlineCount, assetCount, eventCount, fimCount, failedBaselineCount, status);
    }

    private long countAssets(List<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return 0;
        }
        return assetMapper.selectCount(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getDeleted, 0)
                .in(SocAsset::getSourceType, sourceTypes));
    }

    private long countExternalEvents(List<String> sourceTypes, LocalDateTime since, boolean demoOnly) {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .ge(SocExternalEvent::getEventTime, since);
        if (demoOnly) {
            wrapper.isNotNull(SocExternalEvent::getDemoCaseId);
        } else if (sourceTypes != null && !sourceTypes.isEmpty()) {
            wrapper.in(SocExternalEvent::getSourceType, sourceTypes);
        } else {
            return 0;
        }
        return externalEventMapper.selectCount(wrapper);
    }

    private long countFimEvents(List<String> sourceTypes, LocalDateTime since) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return 0;
        }
        return fimMapper.selectCount(new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getDeleted, 0)
                .in(SocFileIntegrityEvent::getSourceType, sourceTypes)
                .ge(SocFileIntegrityEvent::getEventTime, since));
    }

    private long countFailedBaselines(List<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return 0;
        }
        return baselineMapper.selectCount(new LambdaQueryWrapper<SocBaselineCheck>()
                .eq(SocBaselineCheck::getDeleted, 0)
                .in(SocBaselineCheck::getSourceType, sourceTypes)
                .ne(SocBaselineCheck::getStatus, "passed"));
    }

    private long countBatches(LocalDateTime since, String agentId) {
        return batchMapper.selectCount(new LambdaQueryWrapper<SocIngestBatch>()
                .eq(notBlank(agentId), SocIngestBatch::getAgentId, agentId)
                .ge(SocIngestBatch::getFinishedAt, since));
    }

    private long countRejects(LocalDateTime since, String agentId) {
        return rejectLogMapper.selectCount(new LambdaQueryWrapper<SocIngestRejectLog>()
                .eq(notBlank(agentId), SocIngestRejectLog::getAgentId, agentId)
                .ge(SocIngestRejectLog::getCreatedAt, since));
    }

    private List<RecentHostEvent> recentEventsForSources(List<String> sourceTypes, int limit) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return List.of();
        }
        return externalEventMapper.selectList(new LambdaQueryWrapper<SocExternalEvent>()
                        .eq(SocExternalEvent::getDeleted, 0)
                        .in(SocExternalEvent::getSourceType, sourceTypes)
                        .orderByDesc(SocExternalEvent::getEventTime)
                        .last("LIMIT " + limit))
                .stream()
                .map(this::toRecentEvent)
                .toList();
    }

    private List<RecentHostEvent> recentEventsForAgent(SocHostAgent agent, List<String> batchIds) {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .eq(SocExternalEvent::getSourceType, agentSourceType(agent))
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 10");
        if (batchIds != null && !batchIds.isEmpty()) {
            wrapper.in(SocExternalEvent::getBatchId, batchIds);
        } else {
            String primaryIp = primaryIp(agent.getIpAddressesJson());
            boolean hasHostname = notBlank(agent.getHostname());
            boolean hasPrimaryIp = notBlank(primaryIp);
            if (hasHostname && hasPrimaryIp) {
                wrapper.and(inner -> inner
                        .eq(SocExternalEvent::getAssetName, agent.getHostname())
                        .or()
                        .eq(SocExternalEvent::getAssetIp, primaryIp));
            } else if (hasHostname) {
                wrapper.eq(SocExternalEvent::getAssetName, agent.getHostname());
            } else if (hasPrimaryIp) {
                wrapper.eq(SocExternalEvent::getAssetIp, primaryIp);
            }
        }
        return externalEventMapper.selectList(wrapper).stream().map(this::toRecentEvent).toList();
    }

    private SocHostAgent sanitizeAgent(SocHostAgent agent, LocalDateTime now) {
        agent.setStatus(effectiveStatus(agent, now));
        agent.setTokenHash(null);
        return agent;
    }

    private String effectiveStatus(SocHostAgent agent, LocalDateTime now) {
        if (!"online".equalsIgnoreCase(firstNotBlank(agent.getStatus(), "")) || agent.getLastSeenAt() == null) {
            return "offline";
        }
        return agent.getLastSeenAt().isAfter(now.minusMinutes(AGENT_ONLINE_WINDOW_MINUTES)) ? "online" : "offline";
    }

    private RecentHostEvent toRecentEvent(SocExternalEvent event) {
        return new RecentHostEvent(
                event.getId(),
                event.getEventUid(),
                event.getSourceType(),
                event.getEventType(),
                event.getSeverity(),
                event.getRuleName(),
                event.getAssetName(),
                event.getAssetIp(),
                event.getBatchId(),
                event.getStatus(),
                event.getEventTime()
        );
    }

    private AgentBatchItem toBatchItem(SocIngestBatch batch) {
        return new AgentBatchItem(
                batch.getId(),
                batch.getBatchId(),
                batch.getIngestType(),
                batch.getItemCount(),
                batch.getAcceptedCount(),
                batch.getDuplicateCount(),
                batch.getRejectedCount(),
                batch.getStatus(),
                batch.getFinishedAt(),
                batch.getErrorMessage()
        );
    }

    private AgentRejectItem toRejectItem(SocIngestRejectLog rejectLog) {
        return new AgentRejectItem(
                rejectLog.getId(),
                rejectLog.getBatchId(),
                rejectLog.getIngestType(),
                rejectLog.getEventUid(),
                rejectLog.getReasonCode(),
                rejectLog.getReason(),
                rejectLog.getCreatedAt()
        );
    }

    private String batchId(String agentId, String requestedBatchId, String ingestType) {
        return firstNotBlank(requestedBatchId, "HOST-" + agentId + "-" + ingestType + "-" + LocalDateTime.now().format(BATCH_TIME_FORMAT));
    }

    private String defaultAgentId(String osType, String hostname) {
        return normalizeOs(osType) + "-" + hostname.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sourceType(SocHostAgent agent, String sourceModule) {
        return agentSourceType(agent);
    }

    private String agentSourceType(SocHostAgent agent) {
        return switch (normalizeOs(agent.getOsType())) {
            case "windows" -> "windows-agent";
            case "macos" -> "macos-agent";
            default -> "host-agent";
        };
    }

    private String normalizeSource(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
    }

    private String normalizeOs(String osType) {
        String normalized = firstNotBlank(osType, "unknown").toLowerCase(Locale.ROOT);
        if (normalized.contains("darwin") || normalized.contains("mac")) {
            return "macos";
        }
        if (normalized.contains("win")) {
            return "windows";
        }
        if (normalized.contains("linux")) {
            return "linux";
        }
        return normalized;
    }

    private String correlationKey(String batchId, String assetIp, String ruleId, String eventType) {
        return String.join("|",
                firstNotBlank(batchId, "host"),
                firstNotBlank(assetIp, "unknown-asset"),
                firstNotBlank(ruleId, eventType, "host-event"));
    }

    private String primaryIp(String ipAddressesJson) {
        if (!notBlank(ipAddressesJson)) {
            return null;
        }
        try {
            List<?> ips = objectMapper.readValue(ipAddressesJson, List.class);
            return ips.stream().filter(Objects::nonNull).map(String::valueOf).findFirst().orElse(null);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private record SourceGroup(
            String sourceType,
            String label,
            List<String> osTypes,
            List<String> eventSourceTypes,
            boolean demo
    ) {
    }
}
