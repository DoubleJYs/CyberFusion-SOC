package com.zhangjiyan.template.soc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.excel.ExcelExportUtils;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.pdf.SimplePdfUtils;
import com.zhangjiyan.template.soc.alert.AlertActionRequest;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheck;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.client.ClientLabEventRequest;
import com.zhangjiyan.template.soc.client.ClientSnapshotRequest;
import com.zhangjiyan.template.soc.client.ClientTerminalCommandRequest;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.correlation.SocIncidentClusterMapper;
import com.zhangjiyan.template.soc.demo.DemoRangeBatchImportRequest;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.dto.SocStatusRequest;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.external.SuricataImportRequest;
import com.zhangjiyan.template.soc.external.CyberChefAnalysisRequest;
import com.zhangjiyan.template.soc.external.CyberFusionImportRequest;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEvent;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import com.zhangjiyan.template.soc.noise.AlertWhitelistRequest;
import com.zhangjiyan.template.soc.noise.SocAlertWhitelist;
import com.zhangjiyan.template.soc.noise.SocAlertWhitelistMapper;
import com.zhangjiyan.template.soc.notification.SocNotificationLog;
import com.zhangjiyan.template.soc.notification.SocNotificationService;
import com.zhangjiyan.template.soc.policy.LocalCheckPolicyService;
import com.zhangjiyan.template.soc.policy.adapter.EventAdapterPolicyService;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.operations.SocOperationsService;
import com.zhangjiyan.template.soc.report.ReportGenerateRequest;
import com.zhangjiyan.template.soc.report.ReportExportPreview;
import com.zhangjiyan.template.soc.report.SocReport;
import com.zhangjiyan.template.soc.report.SocReportMapper;
import com.zhangjiyan.template.soc.settings.SocSyncTask;
import com.zhangjiyan.template.soc.settings.SocSyncTaskMapper;
import com.zhangjiyan.template.soc.settings.SocWazuhConfig;
import com.zhangjiyan.template.soc.settings.SocWazuhConfigMapper;
import com.zhangjiyan.template.soc.ticket.*;
import com.zhangjiyan.template.soc.trend.TrendAnomalyService;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HexFormat;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

@Service
@RequiredArgsConstructor
public class SocOperationService {

    private final SocAlertMapper alertMapper;
    private final SocAssetMapper assetMapper;
    private final SocTicketMapper ticketMapper;
    private final SocTicketTimelineMapper timelineMapper;
    private final SocReportMapper reportMapper;
    private final SocWazuhConfigMapper wazuhConfigMapper;
    private final SocSyncTaskMapper syncTaskMapper;
    private final SocVulnerabilityMapper vulnerabilityMapper;
    private final SocBaselineCheckMapper baselineMapper;
    private final SocFileIntegrityEventMapper fileIntegrityMapper;
    private final SocAlertWhitelistMapper whitelistMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocIncidentClusterMapper incidentClusterMapper;
    private final SocTicketTaskMapper ticketTaskMapper;
    private final TicketStateMachine ticketStateMachine;
    private final SocSecurityScope securityScope;
    private final SocNotificationService notificationService;
    private final LocalCheckPolicyService localCheckPolicyService;
    private final EventAdapterPolicyService eventAdapterPolicyService;
    private final TrendAnomalyService trendAnomalyService;
    private final SocOperationsService operationsService;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_DEMO_RANGE_BATCH_ID = "DEMO-RANGE-OFFLINE-V1";
    private static final String DEMO_SEED_BATCH_ID = "DEMO-SEED-BASELINE";
    private static final String HOST_AGENT_INCIDENT_SMOKE_PREFIX = "HOST-AGENT-INCIDENT-SMOKE-";
    private static final String DEMO_DATA_IMPORT_SCRIPT = "sql/soc-demo-data-import.sql";
    private static final String DEMO_DATA_CLEAR_SCRIPT = "sql/soc-demo-data-clear.sql";
    private static final List<String> NON_REAL_SOURCE_TYPES = List.of("demo", "mock", "local-demo-client", "fixture");
    private static final String NON_REAL_SOURCE_SQL = "'demo','mock','local-demo-client','fixture'";

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private static final List<DemoRangeSourceSample> DEMO_RANGE_SOURCE_SAMPLES = List.of(
            new DemoRangeSourceSample("waf", """
                    {"sourceType":"waf","eventType":"waf_block","severity":"high","assetIp":"10.20.1.15","targetUrl":"https://demo.internal.local/admin","httpMethod":"POST","httpStatus":403,"action":"block","ruleId":"WAF-DEMO-1001","ruleName":"Admin route protected by WAF policy","engine":"CyberFusion Demo Gateway","requestId":"{{batchId}}-waf-0001","demoCaseId":"access-control-risk","batchId":"{{batchId}}","evidenceSummary":"Demo gateway blocked restricted admin access before it reached prod-app-01.","timestamp":"2026-06-18T10:00:00+08:00","sourceIp":"203.0.113.80"}
                    {"sourceType":"waf","eventType":"upload_block","severity":"high","assetIp":"10.20.1.15","targetUrl":"https://demo.internal.local/upload","httpMethod":"POST","httpStatus":403,"action":"block","ruleId":"WAF-DEMO-2001","ruleName":"Unsafe upload metadata blocked by file policy","engine":"CyberFusion Demo Gateway","requestId":"{{batchId}}-waf-0002","demoCaseId":"upload-policy-risk","batchId":"{{batchId}}","evidenceSummary":"Demo gateway blocked upload metadata and retained SOC evidence without storing file content.","timestamp":"2026-06-18T10:02:00+08:00","sourceIp":"203.0.113.81"}
                    """),
            new DemoRangeSourceSample("zap", """
                    [
                      {"pluginid":"10021","name":"Demo protective header review","riskdesc":"Medium","url":"https://demo.internal.local/login?batch={{batchId}}","batchId":"{{batchId}}","demoCaseId":"input-validation-risk","evidenceSummary":"Offline ZAP-style finding used for SOC validation only."}
                    ]
                    """),
            new DemoRangeSourceSample("trivy", """
                    {"Results":[{"Target":"prod-app-01/{{batchId}}","Vulnerabilities":[{"VulnerabilityID":"CVE-2026-DEMO-RANGE-0001","PkgName":"demo-range-web-lib","InstalledVersion":"1.0.0","FixedVersion":"1.0.1","Severity":"HIGH","Title":"Demo dependency risk for offline SOC validation","batchId":"{{batchId}}","demoCaseId":"dependency-vulnerability"}]}]}
                    """),
            new DemoRangeSourceSample("suricata", """
                    {"timestamp":"2026-06-18T10:03:00+08:00","event_type":"alert","src_ip":"203.0.113.90","dest_ip":"10.20.1.15","flow_id":860001,"alert":{"signature_id":26061801,"signature":"CyberFusion demo IDS policy match","severity":1},"batchId":"{{batchId}}","demoCaseId":"network-ids","evidenceSummary":"Offline Suricata EVE alert showing network IDS evidence."}
                    """),
            new DemoRangeSourceSample("zeek", """
                    {"ts":"1781751780.0","uid":"{{batchId}}-ZEEK-0001","id.orig_h":"203.0.113.91","id.resp_h":"10.20.1.15","proto":"tcp","service":"https","batchId":"{{batchId}}","demoCaseId":"network-ids","evidenceSummary":"Offline Zeek connection metadata supporting IDS triage."}
                    """),
            new DemoRangeSourceSample("wazuh", """
                    {"timestamp":"2026-06-18T10:05:00+08:00","id":"{{batchId}}-wazuh-fim-0001","rule":{"id":"550","level":9,"description":"Demo FIM policy observed a protected configuration change"},"agent":{"name":"prod-app-01","ip":"10.20.1.15"},"data":{"srcip":"127.0.0.1","path":"/etc/demo-app/policy.conf"},"batchId":"{{batchId}}","demoCaseId":"fim","evidenceSummary":"Offline Wazuh-style FIM event for evidence-chain demonstration."}
                    """)
    );

    public DashboardOverview dashboardOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayAlerts = alertMapper.selectCount(scopedAlertWrapper().ge(SocAlert::getEventTime, todayStart));
        long highAlerts = alertMapper.selectCount(scopedAlertWrapper()
                .in(SocAlert::getSeverity, List.of("critical", "high"))
                .notIn(SocAlert::getStatus, List.of("closed", "ignored", "false_positive")));
        long pendingTickets = ticketMapper.selectCount(scopedTicketWrapper()
                .notIn(SocTicket::getStatus, List.of("已关闭", "已归档")));
        long assets = assetMapper.selectCount(scopedAssetWrapper());
        long unhandledAlerts = alertMapper.selectCount(scopedAlertWrapper().eq(SocAlert::getStatus, "new"));
        return new DashboardOverview(todayAlerts, highAlerts, pendingTickets, assets, unhandledAlerts);
    }

    public List<TrendItem> alertTrend() {
        List<TrendItem> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            long count = alertMapper.selectCount(scopedAlertWrapper()
                    .ge(SocAlert::getEventTime, day.atStartOfDay())
                    .lt(SocAlert::getEventTime, day.plusDays(1).atStartOfDay()));
            result.add(new TrendItem(day.toString(), count));
        }
        return result;
    }

    public List<NameValue> severityDistribution() {
        return List.of("critical", "high", "medium", "low").stream()
                .map(severity -> new NameValue(severity, alertMapper.selectCount(scopedAlertWrapper()
                        .eq(SocAlert::getSeverity, severity))))
                .toList();
    }

    public List<NameValue> affectedAssets() {
        return assetMapper.selectList(scopedAssetWrapper()
                        .orderByDesc(SocAsset::getOpenAlertCount)
                        .last("LIMIT 8"))
                .stream()
                .map(asset -> new NameValue(asset.getHostname() + " / " + asset.getIp(), asset.getOpenAlertCount() == null ? 0 : asset.getOpenAlertCount()))
                .toList();
    }

    public RiskAnalytics riskAnalytics() {
        List<SocAsset> assets = assetMapper.selectList(scopedAssetWrapper()
                .orderByDesc(SocAsset::getOpenAlertCount)
                .orderByDesc(SocAsset::getLastSeenAt));
        List<AssetRiskScore> assetRisks = assets.stream()
                .map(this::assetRiskScore)
                .sorted(Comparator.comparing(AssetRiskScore::score).reversed())
                .limit(8)
                .toList();
        List<AlertPriorityScore> alertPriorities = recentScopedAlerts(20).stream()
                .map(this::alertPriorityScore)
                .sorted(Comparator.comparing(AlertPriorityScore::score).reversed())
                .limit(8)
                .toList();
        List<DepartmentRisk> departmentRisks = departmentRisks(assets);
        OperationMetrics operationMetrics = operationMetrics();
        List<SecurityTimelineItem> eventTimeline = securityTimeline();
        return new RiskAnalytics(assetRisks, alertPriorities, departmentRisks, operationMetrics, eventTimeline);
    }

    public PageResult<SocAlert> alerts(SocPageRequest request) {
        LambdaQueryWrapper<SocAlert> wrapper = scopedAlertWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocAlert::getAlertUid, request.keyword())
                        .or().like(SocAlert::getRuleId, request.keyword())
                        .or().like(SocAlert::getRuleDescription, request.keyword())
                        .or().like(SocAlert::getAssetName, request.keyword())
                        .or().like(SocAlert::getAssetIp, request.keyword())
                        .or().like(SocAlert::getSourceIp, request.keyword())
                        .or().like(SocAlert::getEventType, request.keyword())
                        .or().like(SocAlert::getEvidenceSummary, request.keyword())
                        .or().like(SocAlert::getBatchId, request.keyword())
                        .or().like(SocAlert::getCorrelationKey, request.keyword())
                        .or().like(SocAlert::getRawRef, request.keyword()))
                .and("wazuh".equalsIgnoreCase(String.valueOf(request.sourceType())),
                        w -> w.eq(SocAlert::getSourceType, "wazuh")
                                .or(nested -> nested.eq(SocAlert::getSourceType, "mock").like(SocAlert::getRawRef, "wazuh")))
                .eq(notBlank(request.sourceType()) && !"wazuh".equalsIgnoreCase(request.sourceType()), SocAlert::getSourceType, request.sourceType())
                .eq(notBlank(request.severity()), SocAlert::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocAlert::getStatus, request.status())
                .orderByDesc(SocAlert::getEventTime);
        Page<SocAlert> page = alertMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper);
        annotateAlertNoise(page.getRecords());
        return PageResult.from(page);
    }

    public SocAlert alertDetail(Long id) {
        SocAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException("告警不存在");
        }
        ensureAlertAccess(alert);
        enrichAlertEvidence(alert);
        return alert;
    }

    @Transactional
    public SocAlert updateAlertStatus(Long id, String status, AlertActionRequest request) {
        SocAlert alert = alertDetail(id);
        alert.setStatus(status);
        if ("acknowledged".equals(status)) {
            alert.setAcknowledgedAt(LocalDateTime.now());
        }
        if ("closed".equals(status) || "ignored".equals(status) || "false_positive".equals(status)) {
            alert.setClosedAt(LocalDateTime.now());
        }
        alertMapper.updateById(alert);
        return alert;
    }

    @Transactional
    public SocTicket createTicket(Long alertId, AlertActionRequest request) {
        SocAlert alert = alertDetail(alertId);
        if (alert.getTicketId() != null) {
            SocTicket existing = ticketMapper.selectById(alert.getTicketId());
            if (existing != null) {
                return existing;
            }
        }
        SocTicket ticket = new SocTicket();
        DemoEvidenceFields evidence = alertEvidenceFields(alert);
        String sourceRemark = demoTicketSourceRemark(evidence);
        ticket.setTicketNo("INC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + alert.getId());
        ticket.setAlertId(alert.getId());
        String demoTitleSuffix = notBlank(evidence.batchId()) ? "（Demo Range " + evidence.batchId() + "）" : "";
        ticket.setTitle(alert.getSeverity().toUpperCase(Locale.ROOT) + " 告警处置：" + alert.getRuleDescription() + demoTitleSuffix);
        ticket.setSeverity(alert.getSeverity());
        ticket.setStatus("待分派");
        ticket.setAssigneeId(request.assigneeId());
        ticket.setDeptId(alert.getDeptId());
        ticket.setDueAt(LocalDateTime.now().plusHours("critical".equals(alert.getSeverity()) ? 4 : 24));
        ticketMapper.insert(ticket);
        appendTimeline(ticket.getId(), "转工单", null, "待分派",
                firstNotBlank(request.remark(), "告警转工单") + (notBlank(sourceRemark) ? "；" + sourceRemark : ""));
        if (notBlank(sourceRemark)) {
            appendTimeline(ticket.getId(), "Demo Range 来源", null, "待分派", sourceRemark);
        }
        alert.setTicketId(ticket.getId());
        alert.setStatus("ticketed");
        alertMapper.updateById(alert);
        dispatchNotification("alert_ticketed", alert.getSeverity(), "alert", alert.getId(),
                "告警已转工单：" + alert.getAlertUid(),
                alert.getSeverity().toUpperCase(Locale.ROOT) + " 告警已进入工单处置流程，工单号：" + ticket.getTicketNo());
        return ticket;
    }

    public PageResult<SocAsset> assets(SocPageRequest request) {
        LambdaQueryWrapper<SocAsset> wrapper = scopedAssetWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocAsset::getHostname, request.keyword()).or().like(SocAsset::getIp, request.keyword()))
                .eq(notBlank(request.riskLevel()), SocAsset::getRiskLevel, request.riskLevel())
                .orderByDesc(SocAsset::getOpenAlertCount)
                .orderByDesc(SocAsset::getLastSeenAt);
        return PageResult.from(assetMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public List<SocAsset> clientDevices() {
        return assetMapper.selectList(scopedAssetWrapper()
                .orderByDesc(SocAsset::getOpenAlertCount)
                .orderByDesc(SocAsset::getLastSeenAt));
    }

    public ClientDeviceProfile clientDeviceProfile(String ip) {
        SocAsset asset = accessibleAssetByIp(ip);
        String hostname = asset.getHostname();
        List<SocAlert> alerts = alertMapper.selectList(scopedAlertWrapper()
                .and(w -> w.eq(SocAlert::getAssetIp, asset.getIp()).or().eq(SocAlert::getAssetName, hostname))
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT 80"));
        annotateAlertNoise(alerts);
        List<SocVulnerability> vulnerabilities = vulnerabilityMapper.selectList(scopedVulnerabilityWrapper()
                .and(w -> w.eq(SocVulnerability::getAssetIp, asset.getIp()).or().eq(SocVulnerability::getAssetName, hostname))
                .orderByDesc(SocVulnerability::getDetectedAt)
                .last("LIMIT 80"));
        List<SocBaselineCheck> baselines = baselineMapper.selectList(scopedBaselineWrapper()
                .and(w -> w.eq(SocBaselineCheck::getAssetIp, asset.getIp()).or().eq(SocBaselineCheck::getAssetName, hostname))
                .orderByDesc(SocBaselineCheck::getCheckedAt)
                .last("LIMIT 80"));
        List<SocFileIntegrityEvent> fileIntegrityEvents = fileIntegrityMapper.selectList(scopedFileIntegrityWrapper()
                .and(w -> w.eq(SocFileIntegrityEvent::getAssetIp, asset.getIp()).or().eq(SocFileIntegrityEvent::getHostname, hostname))
                .orderByDesc(SocFileIntegrityEvent::getEventTime)
                .last("LIMIT 80"));
        List<SocExternalEvent> externalEvents = externalEventMapper.selectList(scopedExternalEventWrapper()
                .and(w -> w.eq(SocExternalEvent::getAssetIp, asset.getIp())
                        .or().eq(SocExternalEvent::getAssetName, hostname)
                        .or().eq(SocExternalEvent::getSrcIp, asset.getIp())
                        .or().eq(SocExternalEvent::getDestIp, asset.getIp()))
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 80"));
        long openVulnerabilities = vulnerabilities.stream().filter(item -> !List.of("fixed", "accepted").contains(item.getStatus())).count();
        long failedBaselines = baselines.stream().filter(item -> "failed".equals(item.getResult()) || !List.of("passed", "confirmed").contains(item.getStatus())).count();
        long pendingFim = fileIntegrityEvents.stream().filter(item -> !List.of("confirmed", "ignored", "closed").contains(item.getStatus())).count();
        long pendingExternal = externalEvents.stream().filter(item -> !List.of("linked", "ignored", "closed").contains(item.getStatus())).count();
        int score = Math.min(100, riskBase(asset.getRiskLevel()) + alerts.size() * 4 + (int) openVulnerabilities * 5 + (int) failedBaselines * 3 + (int) pendingFim * 2 + (int) pendingExternal * 2);
        ClientDeviceMetrics metrics = new ClientDeviceMetrics(score, alerts.size(), openVulnerabilities, failedBaselines, pendingFim, pendingExternal,
                "%d 告警 / %d 漏洞 / %d 证据待复核".formatted(alerts.size(), openVulnerabilities, failedBaselines + pendingFim + pendingExternal));
        List<ClientEvidenceItem> timeline = clientEvidenceTimeline(alerts, vulnerabilities, baselines, fileIntegrityEvents, externalEvents);
        return new ClientDeviceProfile(asset, metrics, alerts, vulnerabilities, baselines, fileIntegrityEvents, externalEvents, timeline);
    }

    @Transactional
    public ClientLabEventResult submitClientLabEvent(ClientLabEventRequest request) {
        SocAsset asset = accessibleAssetByIp(request.assetIp());
        return submitLabEventForAsset(request, asset, securityScope.currentUsername(), securityScope.currentUserId(), securityScope.currentDeptId(), false);
    }

    @Transactional
    public ClientLabEventResult submitLocalDemoLabEvent(ClientLabEventRequest request, String remoteAddr) {
        ensureLoopbackClient(remoteAddr);
        SocAsset asset = localDemoAssetByIp(request.assetIp());
        return submitLabEventForAsset(request, asset, "local-demo-client", null, null, true);
    }

    private ClientLabEventResult submitLabEventForAsset(ClientLabEventRequest request,
                                                        SocAsset asset,
                                                        String operator,
                                                        Long fallbackOwnerId,
                                                        Long fallbackDeptId,
                                                        boolean localDemoClient) {
        LabAction action = labAction(request.actionType());
        LocalDateTime now = LocalDateTime.now();
        String note = limit(firstNotBlank(request.note(), "本地授权演练动作"), 240);
        String targetName = limit(firstNotBlank(request.targetName(), "未命名本地演练目标"), 80);
        String targetType = limit(firstNotBlank(request.targetType(), "local_site"), 32);
        String targetAddress = limit(firstNotBlank(request.targetAddress(), "local-authorized-target"), 160);
        String targetScope = limit(firstNotBlank(request.targetScope(), "仅限本地授权站点或虚拟机"), 160);
        String sessionId = limit(firstNotBlank(request.sessionId(), "LABS-" + hash(asset.getIp() + now.toString())), 80);
        String sessionName = limit(firstNotBlank(request.sessionName(), "本地授权演练会话"), 80);
        String sessionPhase = limit(firstNotBlank(request.sessionPhase(), "observe"), 32);
        String operatorNote = limit(firstNotBlank(request.operatorNote(), "-"), 240);
        String eventUid = "LAB-" + hash(String.join("|", request.actionType(), asset.getIp(), operator, now.toString(), UUID.randomUUID().toString()));
        Map<String, Object> rawEventPayload = new LinkedHashMap<>();
        rawEventPayload.put("source", "osquery");
        rawEventPayload.put("scenario", action.title());
        rawEventPayload.put("action_type", request.actionType());
        rawEventPayload.put("session_id", sessionId);
        rawEventPayload.put("session_name", sessionName);
        rawEventPayload.put("session_phase", sessionPhase);
        rawEventPayload.put("operator_note", operatorNote);
        rawEventPayload.put("target_name", targetName);
        rawEventPayload.put("target_type", targetType);
        rawEventPayload.put("target_address", targetAddress);
        rawEventPayload.put("target_scope", targetScope);
        rawEventPayload.put("asset_ip", asset.getIp());
        rawEventPayload.put("asset", asset.getHostname());
        rawEventPayload.put("operator", operator);
        rawEventPayload.put("note", note);
        rawEventPayload.put("authorized_lab", true);
        rawEventPayload.put("controlled_demo", true);
        rawEventPayload.put("simulated", false);
        rawEventPayload.put("local_demo_client", localDemoClient);
        rawEventPayload.put("occurred_at", now.toString());
        String rawEvent = writeJson(rawEventPayload);
        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        normalizedPayload.put("source", "osquery");
        normalizedPayload.put("event_type", action.eventType());
        normalizedPayload.put("severity", action.severity());
        normalizedPayload.put("asset", asset.getHostname());
        normalizedPayload.put("rule", action.title());
        normalizedPayload.put("target", targetName);
        normalizedPayload.put("session_id", sessionId);
        normalizedPayload.put("session_phase", sessionPhase);
        normalizedPayload.put("authorized_lab", true);
        normalizedPayload.put("controlled_demo", true);
        normalizedPayload.put("local_demo_client", localDemoClient);
        String normalizedEvent = writeJson(normalizedPayload);
        SocExternalEvent event = new SocExternalEvent();
        event.setEventUid(eventUid);
        event.setSourceType("osquery");
        event.setEventType(action.eventType());
        event.setSeverity(action.severity());
        event.setRuleId("LAB-" + request.actionType().toUpperCase(Locale.ROOT));
        event.setRuleName(action.title());
        event.setSrcIp("127.0.0.1");
        event.setDestIp(asset.getIp());
        event.setAssetName(asset.getHostname());
        event.setAssetIp(asset.getIp());
        event.setIoc(action.ioc());
        event.setRawEvent(rawEvent);
        event.setNormalizedEvent(normalizedEvent);
        event.setStatus("new");
        event.setOwnerId(asset.getOwnerId() == null ? fallbackOwnerId : asset.getOwnerId());
        event.setDeptId(asset.getDeptId() == null ? fallbackDeptId : asset.getDeptId());
        event.setEventTime(now);
        externalEventMapper.insert(event);

        SocAlert alert = null;
        boolean linkAlert = request.linkAlert() == null || Boolean.TRUE.equals(request.linkAlert());
        if (linkAlert && action.createAlert()) {
            alert = new SocAlert();
            alert.setAlertUid("LAB-ALERT-" + hash(eventUid));
            alert.setSourceType("osquery");
            alert.setLevel(levelOf(action.severity()));
            alert.setSeverity(action.severity());
            alert.setRuleId(event.getRuleId());
            alert.setRuleDescription(action.title());
            alert.setAssetName(asset.getHostname());
            alert.setAssetIp(asset.getIp());
            alert.setSourceIp("127.0.0.1");
            alert.setStatus("new");
            alert.setTactic(action.tactic());
            alert.setRawRef("client-lab/" + eventUid);
            alert.setEventTime(now);
            alert.setOwnerId(event.getOwnerId());
            alert.setDeptId(event.getDeptId());
            alertMapper.insert(alert);
            event.setAlertId(alert.getId());
            event.setStatus("linked");
            externalEventMapper.updateById(event);
        }
        return new ClientLabEventResult(event, alert == null ? null : alert.getId(),
                alert == null ? "已写入用户端演练事件" : "已写入用户端演练事件并生成统一告警");
    }

    @Transactional
    public ClientTerminalCommandResult runClientTerminalCommand(ClientTerminalCommandRequest request) {
        SocAsset asset = accessibleAssetByIp(request.assetIp());
        return runTerminalCommandForAsset(request, asset, securityScope.currentUsername(), securityScope.currentUserId(), securityScope.currentDeptId(), false);
    }

    @Transactional
    public ClientTerminalCommandResult runLocalDemoTerminalCommand(ClientTerminalCommandRequest request, String remoteAddr) {
        ensureLoopbackClient(remoteAddr);
        SocAsset asset = localDemoAssetByIp(request.assetIp());
        return runTerminalCommandForAsset(request, asset, "local-demo-client", null, null, true);
    }

    @Transactional
    public ClientSecuritySnapshotResult runClientSecuritySnapshot(ClientSnapshotRequest request) {
        SocAsset asset = accessibleAssetByIp(request.assetIp());
        return runSecuritySnapshotForAsset(request, asset, securityScope.currentUsername(), securityScope.currentUserId(), securityScope.currentDeptId(), false);
    }

    @Transactional
    public ClientSecuritySnapshotResult runLocalDemoSecuritySnapshot(ClientSnapshotRequest request, String remoteAddr) {
        ensureLoopbackClient(remoteAddr);
        SocAsset asset = localDemoAssetByIp(request.assetIp());
        return runSecuritySnapshotForAsset(request, asset, "local-demo-client", null, null, true);
    }

    private ClientSecuritySnapshotResult runSecuritySnapshotForAsset(ClientSnapshotRequest request,
                                                                     SocAsset asset,
                                                                     String operator,
                                                                     Long fallbackOwnerId,
                                                                     Long fallbackDeptId,
                                                                     boolean localDemoClient) {
        String osType = resolveTerminalOs(request.osType(), asset);
        List<LocalCheckPolicyService.ResolvedCommand> policyCommands = localCheckPolicyService.snapshotCommands(osType);
        LocalDateTime now = LocalDateTime.now();
        List<ClientSnapshotSection> sections = policyCommands.stream()
                .map(policyCommand -> {
                    TerminalCommandSpec spec = terminalCommandSpec(policyCommand);
                    TerminalExecution execution = executeTerminalCommand(spec);
                    String severity = execution.timeout() || execution.exitCode() != 0 ? "medium" : spec.severity();
                    return new ClientSnapshotSection(spec.commandKey(), spec.label(), spec.displayCommand(),
                            execution.output(), execution.exitCode(), execution.timeout(), severity);
                })
                .toList();
        boolean hasWarning = sections.stream().anyMatch(item -> item.timeout() || item.exitCode() != 0 || List.of("medium", "high", "critical").contains(item.severity()));
        String severity = hasWarning ? "medium" : "low";
        String snapshotId = "SNAP-" + hash(String.join("|", asset.getIp(), operator, now.toString(), UUID.randomUUID().toString()));
        Map<String, Object> rawEventPayload = new LinkedHashMap<>();
        rawEventPayload.put("source", "osquery");
        rawEventPayload.put("scenario", "本机安全快照采集");
        rawEventPayload.put("action_type", "security_snapshot");
        rawEventPayload.put("snapshot_id", snapshotId);
        rawEventPayload.put("asset_ip", asset.getIp());
        rawEventPayload.put("asset", asset.getHostname());
        rawEventPayload.put("operator", operator);
        rawEventPayload.put("operator_note", limit(firstNotBlank(request.note(), "客户现场一键采集本机只读安全快照"), 240));
        rawEventPayload.put("target_name", "客户演示本机");
        rawEventPayload.put("target_type", "local_host");
        rawEventPayload.put("target_address", asset.getHostname() + " / " + asset.getIp());
        rawEventPayload.put("target_scope", localDemoClient ? "仅限本机回环固定白名单只读观察命令" : "仅限固定白名单只读观察命令");
        rawEventPayload.put("authorized_lab", true);
        rawEventPayload.put("controlled_demo", true);
        rawEventPayload.put("simulated", false);
        rawEventPayload.put("local_demo_client", localDemoClient);
        rawEventPayload.put("sections", sections);
        rawEventPayload.put("occurred_at", now.toString());

        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        normalizedPayload.put("source", "osquery");
        normalizedPayload.put("event_type", "host_snapshot");
        normalizedPayload.put("severity", severity);
        normalizedPayload.put("asset", asset.getHostname());
        normalizedPayload.put("rule", "本机安全快照采集");
        normalizedPayload.put("snapshot_id", snapshotId);
        normalizedPayload.put("section_count", sections.size());
        normalizedPayload.put("authorized_lab", true);
        normalizedPayload.put("controlled_demo", true);
        normalizedPayload.put("local_demo_client", localDemoClient);

        SocExternalEvent event = new SocExternalEvent();
        event.setEventUid(snapshotId);
        event.setSourceType("osquery");
        event.setEventType("host_snapshot");
        event.setSeverity(severity);
        event.setRuleId("CLIENT-SECURITY-SNAPSHOT");
        event.setRuleName("本机安全快照采集");
        event.setSrcIp("127.0.0.1");
        event.setDestIp(asset.getIp());
        event.setAssetName(asset.getHostname());
        event.setAssetIp(asset.getIp());
        event.setRawEvent(writeJson(rawEventPayload));
        event.setNormalizedEvent(writeJson(normalizedPayload));
        event.setStatus("new");
        event.setOwnerId(asset.getOwnerId() == null ? fallbackOwnerId : asset.getOwnerId());
        event.setDeptId(asset.getDeptId() == null ? fallbackDeptId : asset.getDeptId());
        event.setEventTime(now);
        externalEventMapper.insert(event);

        SocAlert alert = null;
        boolean linkAlert = request.linkAlert() == null || Boolean.TRUE.equals(request.linkAlert());
        if (linkAlert) {
            alert = new SocAlert();
            alert.setAlertUid("SNAP-ALERT-" + hash(snapshotId));
            alert.setSourceType("osquery");
            alert.setLevel(levelOf(severity));
            alert.setSeverity(severity);
            alert.setRuleId(event.getRuleId());
            alert.setRuleDescription(event.getRuleName());
            alert.setAssetName(asset.getHostname());
            alert.setAssetIp(asset.getIp());
            alert.setSourceIp("127.0.0.1");
            alert.setStatus("new");
            alert.setTactic("Discovery");
            alert.setRawRef("client-snapshot/" + snapshotId);
            alert.setEventTime(now);
            alert.setOwnerId(event.getOwnerId());
            alert.setDeptId(event.getDeptId());
            alertMapper.insert(alert);
            event.setAlertId(alert.getId());
            event.setStatus("linked");
            externalEventMapper.updateById(event);
        }
        return new ClientSecuritySnapshotResult(snapshotId, sections, event, alert == null ? null : alert.getId(),
                alert == null ? "已采集本机安全快照" : "已采集本机安全快照并生成统一告警");
    }

    private ClientTerminalCommandResult runTerminalCommandForAsset(ClientTerminalCommandRequest request,
                                                                   SocAsset asset,
                                                                   String operator,
                                                                   Long fallbackOwnerId,
                                                                   Long fallbackDeptId,
                                                                   boolean localDemoClient) {
        String osType = resolveTerminalOs(request.osType(), asset);
        TerminalCommandSpec spec = terminalCommandSpec(localCheckPolicyService.resolve(request.commandKey(), osType));
        TerminalExecution execution = executeTerminalCommand(spec);
        LocalDateTime now = LocalDateTime.now();
        String eventUid = "TERM-" + hash(String.join("|", spec.commandKey(), asset.getIp(), operator, now.toString(), UUID.randomUUID().toString()));
        Map<String, Object> rawEventPayload = new LinkedHashMap<>();
        rawEventPayload.put("source", "osquery");
        rawEventPayload.put("scenario", "本地授权终端观察");
        rawEventPayload.put("action_type", "terminal_command");
        rawEventPayload.put("command_key", spec.commandKey());
        rawEventPayload.put("terminal_command", spec.displayCommand());
        rawEventPayload.put("terminal_exit_code", execution.exitCode());
        rawEventPayload.put("terminal_timeout", execution.timeout());
        rawEventPayload.put("terminal_output", execution.output());
        rawEventPayload.put("asset_ip", asset.getIp());
        rawEventPayload.put("asset", asset.getHostname());
        rawEventPayload.put("operator", operator);
        rawEventPayload.put("operator_note", limit(firstNotBlank(request.note(), "本地授权终端观察"), 240));
        rawEventPayload.put("target_name", "客户演示本地终端");
        rawEventPayload.put("target_type", "local_terminal");
        rawEventPayload.put("target_address", asset.getHostname() + " / " + asset.getIp());
        rawEventPayload.put("target_scope", localDemoClient ? "仅限本机回环固定白名单只读观察命令" : "仅限固定白名单只读观察命令");
        rawEventPayload.put("authorized_lab", true);
        rawEventPayload.put("simulated", false);
        rawEventPayload.put("local_demo_client", localDemoClient);
        rawEventPayload.put("occurred_at", now.toString());

        String severity = execution.timeout() || execution.exitCode() != 0 ? "medium" : spec.severity();
        SocExternalEvent event = new SocExternalEvent();
        event.setEventUid(eventUid);
        event.setSourceType("osquery");
        event.setEventType("terminal");
        event.setSeverity(severity);
        event.setRuleId("TERM-" + spec.commandKey().toUpperCase(Locale.ROOT));
        event.setRuleName("本地终端观察：" + spec.label());
        event.setSrcIp("127.0.0.1");
        event.setDestIp(asset.getIp());
        event.setAssetName(asset.getHostname());
        event.setAssetIp(asset.getIp());
        event.setRawEvent(writeJson(rawEventPayload));
        event.setNormalizedEvent(writeJson(Map.of(
                "source", "osquery",
                "event_type", "terminal",
                "severity", severity,
                "asset", asset.getHostname(),
                "rule", "本地终端观察：" + spec.label(),
                "command_key", spec.commandKey(),
                "authorized_lab", true,
                "local_demo_client", localDemoClient
        )));
        event.setStatus("new");
        event.setOwnerId(asset.getOwnerId() == null ? fallbackOwnerId : asset.getOwnerId());
        event.setDeptId(asset.getDeptId() == null ? fallbackDeptId : asset.getDeptId());
        event.setEventTime(now);
        externalEventMapper.insert(event);

        SocAlert alert = null;
        boolean linkAlert = request.linkAlert() == null || Boolean.TRUE.equals(request.linkAlert());
        if (linkAlert) {
            alert = new SocAlert();
            alert.setAlertUid("TERM-ALERT-" + hash(eventUid));
            alert.setSourceType("osquery");
            alert.setLevel(levelOf(severity));
            alert.setSeverity(severity);
            alert.setRuleId(event.getRuleId());
            alert.setRuleDescription(event.getRuleName());
            alert.setAssetName(asset.getHostname());
            alert.setAssetIp(asset.getIp());
            alert.setSourceIp("127.0.0.1");
            alert.setStatus("new");
            alert.setTactic("Discovery");
            alert.setRawRef("client-terminal/" + eventUid);
            alert.setEventTime(now);
            alert.setOwnerId(event.getOwnerId());
            alert.setDeptId(event.getDeptId());
            alertMapper.insert(alert);
            event.setAlertId(alert.getId());
            event.setStatus("linked");
            externalEventMapper.updateById(event);
        }
        return new ClientTerminalCommandResult(spec.commandKey(), spec.displayCommand(), execution.output(), execution.exitCode(), execution.timeout(), event, alert == null ? null : alert.getId());
    }

    public List<LocalCheckPolicyService.ClientCommandOption> localCheckCommands(String osType) {
        return localCheckPolicyService.clientCommands(osType);
    }

    public PageResult<SocTicket> tickets(SocPageRequest request) {
        LambdaQueryWrapper<SocTicket> wrapper = scopedTicketWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocTicket::getTicketNo, request.keyword()).or().like(SocTicket::getTitle, request.keyword()))
                .eq(notBlank(request.status()), SocTicket::getStatus, request.status())
                .eq(notBlank(request.severity()), SocTicket::getSeverity, request.severity())
                .orderByDesc(SocTicket::getCreatedAt);
        return PageResult.from(ticketMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public TicketDetail ticketDetail(Long id) {
        SocTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        ensureTicketAccess(ticket);
        List<SocTicketTimeline> timeline = timelineMapper.selectList(new LambdaQueryWrapper<SocTicketTimeline>()
                .eq(SocTicketTimeline::getTicketId, id)
                .orderByAsc(SocTicketTimeline::getCreatedAt));
        List<SocTicketTask> tasks = ticketTaskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getTicketId, id)
                .eq(SocTicketTask::getDeleted, 0)
                .orderByAsc(SocTicketTask::getSortOrder)
                .orderByAsc(SocTicketTask::getId));
        return new TicketDetail(ticket, timeline, tasks);
    }

    @Transactional
    public SocTicket transitionTicket(Long id, TicketTransitionRequest request) {
        SocTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        ensureTicketAccess(ticket);
        String fromStatus = ticket.getStatus();
        ticketStateMachine.validate(fromStatus, request.targetStatus());
        ticket.setStatus(request.targetStatus());
        if (request.assigneeId() != null) {
            ticket.setAssigneeId(request.assigneeId());
        }
        if (notBlank(request.assigneeName())) {
            ticket.setAssigneeName(request.assigneeName());
        }
        if (request.reviewerId() != null) {
            ticket.setReviewerId(request.reviewerId());
        }
        if (notBlank(request.reviewConclusion())) {
            ticket.setReviewConclusion(request.reviewConclusion());
        }
        if (notBlank(request.resolution())) {
            ticket.setResolution(request.resolution());
        }
        if ("已关闭".equals(request.targetStatus())) {
            ticket.setClosedAt(LocalDateTime.now());
            closeLinkedAlert(ticket);
            dispatchNotification("ticket_closed", ticket.getSeverity(), "ticket", ticket.getId(),
                    "工单已关闭：" + ticket.getTicketNo(),
                    ticket.getTitle() + " 已完成处置并关闭。");
        }
        if ("已归档".equals(request.targetStatus())) {
            ticket.setArchivedAt(LocalDateTime.now());
        }
        if ("待复核".equals(request.targetStatus())) {
            dispatchNotification("ticket_review", ticket.getSeverity(), "ticket", ticket.getId(),
                    "工单待复核：" + ticket.getTicketNo(),
                    ticket.getTitle() + " 已提交复核，请按 SOC 流程确认处置结论。");
        }
        ticketMapper.updateById(ticket);
        appendTimeline(ticket.getId(), "状态流转", fromStatus, request.targetStatus(), request.remark());
        return ticket;
    }

    public PageResult<SocReport> reports(SocPageRequest request) {
        LambdaQueryWrapper<SocReport> wrapper = new LambdaQueryWrapper<SocReport>()
                .eq(SocReport::getDeleted, 0)
                .and(notBlank(request.keyword()), w -> w.like(SocReport::getReportNo, request.keyword())
                        .or().like(SocReport::getTitle, request.keyword())
                        .or().like(SocReport::getSummary, request.keyword())
                        .or().like(SocReport::getRecommendation, request.keyword()))
                .eq(notBlank(request.reportType()), SocReport::getReportType, request.reportType())
                .orderByDesc(SocReport::getGeneratedAt);
        return PageResult.from(reportMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    @Transactional
    public SocReport generateReport(ReportGenerateRequest request) {
        if ("security_validation".equals(request.reportType())) {
            return generateSecurityValidationReport(request);
        }
        LocalDate end = request.periodEnd() == null ? LocalDate.now() : request.periodEnd();
        LocalDate start = request.periodStart() == null ? defaultStart(request.reportType(), end) : request.periodStart();
        ReportMetrics metrics = reportMetrics(start, end);
        SocReport report = new SocReport();
        report.setReportNo("RPT-" + request.reportType().toUpperCase(Locale.ROOT) + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        report.setReportType(request.reportType());
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setTitle("企业安全监测" + labelOf(request.reportType()) + "（" + start + " 至 " + end + "）");
        report.setStatus("generated");
        report.setSummary(reportSummary(metrics));
        report.setRecommendation(reportRecommendation(metrics));
        report.setGeneratedAt(LocalDateTime.now());
        reportMapper.insert(report);
        dispatchNotification("report_generated", "medium", "report", report.getId(),
                "SOC 报表已生成：" + report.getReportNo(),
                report.getTitle() + " 已生成，可在报表中心导出 PDF 或 Excel。");
        return report;
    }

    @Transactional
    public SocReport generateSecurityValidationReport(ReportGenerateRequest request) {
        String batchId = normalizeDemoBatchId(request.batchId());
        DemoRangeEvidenceChain chain = demoRangeEvidenceChain(batchId);
        PlaybookTaskMetrics taskMetrics = playbookTaskMetrics(chain.tickets());
        List<SocIncidentCluster> incidents = validationIncidents(batchId);
        SocOperationsService.OperationsOverview operations = operationsOverview();
        SocOperationsService.TopRiskAsset topRiskAsset = operations == null || operations.topRiskAssets().isEmpty()
                ? null
                : operations.topRiskAssets().get(0);
        SocOperationsService.RecommendationAdoptionMetrics recommendationMetrics = operations == null ? null : operations.recommendationAdoption();
        SocOperationsService.ClientTaskMetrics clientTaskMetrics = operations == null ? null : operations.clientTasks();
        SocOperationsService.RiskTrendMetrics riskTrend = operations == null ? null : operations.riskTrend();
        String trendSummary = operations == null || operations.topTrendSources().isEmpty()
                ? trendAnomalySummary()
                : operations.topTrendSources().stream()
                .limit(3)
                .map(item -> firstNotBlank(item.title(), item.sourceType(), "-") + "(" + item.currentCount() + "条, score " + item.anomalyScore() + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
        LocalDate end = request.periodEnd() == null ? LocalDate.now() : request.periodEnd();
        LocalDate start = request.periodStart() == null ? end : request.periodStart();
        String summary = limit(String.join("；",
                "【管理摘要】本次验证批次 " + batchId + " 已形成客户演示主线：" + validationBusinessConclusion(chain, incidents, topRiskAsset, taskMetrics, clientTaskMetrics),
                "【技术证据】多源证据 " + chain.summary().eventCount() + " 条，覆盖 " + chain.summary().sourceCoverage()
                        + "；关联告警 " + chain.summary().alertCount() + " 条，漏洞 " + chain.summary().vulnerabilityCount() + " 条，拦截 " + chain.summary().blockedCount() + " 条",
                "【事件簇与风险】事件簇 " + incidents.size() + " 个，证据关系 " + incidents.stream().mapToInt(item -> nz(item.getEvidenceCount())).sum()
                        + " 条；Top 高风险资产 " + topRiskAssetSummary(topRiskAsset)
                        + "；风险评分变化 24h " + signed(riskTrend == null ? 0 : riskTrend.change24h()) + " / 7d " + signed(riskTrend == null ? 0 : riskTrend.change7d()),
                "【处置进度】工单 " + chain.summary().ticketCount() + " 个，处置剧本任务 " + taskMetrics.totalTasks()
                        + " 个，已完成 " + taskMetrics.completedTasks() + " 个；推荐动作 " + recommendationSummary(recommendationMetrics),
                "【员工配合】员工待办 " + taskMetrics.employeeTasks() + " 个，员工待办完成情况 " + clientTaskSummary(clientTaskMetrics),
                "【趋势与运营】趋势异常 " + trendSummary + "；" + operationMetricsSummary(),
                "【安全边界】本报告仅使用离线演示和授权导入数据，未扫描公网、未发送真实通知、未执行攻击；通知仅以 dry-run 写入 " + chain.summary().notificationLogCount() + " 条留痕"
        ), 1000);
        String recommendation = limit(String.join("；",
                validationBusinessConclusion(chain, incidents, topRiskAsset, taskMetrics, clientTaskMetrics),
                "建议优先查看 /showcase 的故事线，再进入事件簇说明证据为什么相关，随后从告警或事件簇转工单。",
                "推荐动作应围绕 Web 网关阻断、依赖修复、本机检查和工单关闭推进；员工待办完成后再生成最终验收报告。",
                "导出报告复用现有 Excel/PDF 能力；继续保持 dry-run 通知，不启用真实邮件、Webhook 或外部发送。"
        ), 1000);
        SocReport report = new SocReport();
        report.setReportNo("RPT-VALIDATION-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        report.setReportType("security_validation");
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setTitle("安全验证报告（" + batchId + "）");
        report.setStatus("generated");
        report.setSummary(summary);
        report.setRecommendation(recommendation);
        report.setGeneratedAt(LocalDateTime.now());
        reportMapper.insert(report);
        dispatchNotification("security_validation_report", "medium", "report", report.getId(),
                "安全验证报告已生成：" + batchId,
                "批次 " + batchId + " 安全验证报告已生成，事件/告警/工单/通知 dry-run 证据可在 SOC 页面复核。");
        return report;
    }

    public byte[] exportReport(Long id, String format) {
        SocReport report = reportById(id);
        if ("pdf".equalsIgnoreCase(format)) {
            return SimplePdfUtils.writeDocument(report.getTitle(), reportPreviewLines(report));
        }
        return ExcelExportUtils.export("SOC报表", List.of("模块", "指标", "内容"), reportRows(report));
    }

    public ReportExportPreview reportExportPreview(Long id, String format) {
        SocReport report = reportById(id);
        String normalizedFormat = "pdf".equalsIgnoreCase(format) ? "pdf" : "xlsx";
        return new ReportExportPreview(
                report.getId(),
                report.getReportNo(),
                report.getTitle(),
                normalizedFormat,
                reportExportFilename(report, normalizedFormat),
                "pdf".equals(normalizedFormat)
                        ? "application/pdf"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                List.of("模块", "指标", "内容"),
                reportRows(report),
                reportPreviewLines(report)
        );
    }

    public List<SocWazuhConfig> wazuhConfigs() {
        return wazuhConfigMapper.selectList(new LambdaQueryWrapper<SocWazuhConfig>().orderByDesc(SocWazuhConfig::getUpdatedAt));
    }

    public List<SocSyncTask> syncTasks() {
        return syncTaskMapper.selectList(new LambdaQueryWrapper<SocSyncTask>().orderByAsc(SocSyncTask::getId));
    }

    public PageResult<SocVulnerability> vulnerabilities(SocPageRequest request) {
        LambdaQueryWrapper<SocVulnerability> wrapper = scopedVulnerabilityWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocVulnerability::getCveId, request.keyword())
                        .or().like(SocVulnerability::getAssetName, request.keyword())
                        .or().like(SocVulnerability::getAssetIp, request.keyword())
                        .or().like(SocVulnerability::getSoftwareName, request.keyword())
                        .or().like(SocVulnerability::getFixSuggestion, request.keyword()))
                .eq(notBlank(request.sourceType()), SocVulnerability::getSourceType, request.sourceType())
                .eq(notBlank(request.severity()), SocVulnerability::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocVulnerability::getStatus, request.status())
                .orderByDesc(SocVulnerability::getDetectedAt);
        return PageResult.from(vulnerabilityMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public SocVulnerability vulnerabilityDetail(Long id) {
        SocVulnerability item = vulnerabilityMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("漏洞记录不存在");
        }
        ensureAccess(item.getOwnerId(), item.getDeptId(), "无权访问该漏洞记录");
        return item;
    }

    @Transactional
    public SocVulnerability updateVulnerabilityStatus(Long id, SocStatusRequest request) {
        SocVulnerability item = vulnerabilityDetail(id);
        validateStatus(request.targetStatus(), "open", "fixing", "reviewing", "fixed", "accepted");
        item.setStatus(request.targetStatus());
        if ("fixed".equals(request.targetStatus()) || "accepted".equals(request.targetStatus())) {
            item.setFixedAt(LocalDateTime.now());
        }
        vulnerabilityMapper.updateById(item);
        return item;
    }

    public List<NameValue> vulnerabilitySummary() {
        return List.of("critical", "high", "medium", "low").stream()
                .map(severity -> new NameValue(severity, vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper().eq(SocVulnerability::getSeverity, severity))))
                .toList();
    }

    public PageResult<SocBaselineCheck> baselines(SocPageRequest request) {
        LambdaQueryWrapper<SocBaselineCheck> wrapper = scopedBaselineWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocBaselineCheck::getCheckCode, request.keyword())
                        .or().like(SocBaselineCheck::getCheckItem, request.keyword())
                        .or().like(SocBaselineCheck::getAssetName, request.keyword())
                        .or().like(SocBaselineCheck::getAssetIp, request.keyword()))
                .eq(notBlank(request.category()), SocBaselineCheck::getCategory, request.category())
                .eq(notBlank(request.result()), SocBaselineCheck::getResult, request.result())
                .eq(notBlank(request.status()), SocBaselineCheck::getStatus, request.status())
                .orderByAsc(SocBaselineCheck::getResult)
                .orderByDesc(SocBaselineCheck::getCheckedAt);
        return PageResult.from(baselineMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public SocBaselineCheck baselineDetail(Long id) {
        SocBaselineCheck item = baselineMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("基线核查记录不存在");
        }
        ensureAccess(item.getOwnerId(), item.getDeptId(), "无权访问该基线核查记录");
        return item;
    }

    @Transactional
    public SocBaselineCheck updateBaselineStatus(Long id, SocStatusRequest request) {
        SocBaselineCheck item = baselineDetail(id);
        validateStatus(request.targetStatus(), "failed", "remediating", "reviewing", "passed", "accepted");
        item.setStatus(request.targetStatus());
        if ("passed".equals(request.targetStatus()) || "accepted".equals(request.targetStatus())) {
            item.setReviewedAt(LocalDateTime.now());
        }
        baselineMapper.updateById(item);
        return item;
    }

    public List<NameValue> baselineSummary() {
        return List.of("SSH", "PASSWORD", "FIREWALL", "SERVICE", "FILE_PERMISSION").stream()
                .map(category -> new NameValue(category, baselineMapper.selectCount(scopedBaselineWrapper().eq(SocBaselineCheck::getCategory, category))))
                .toList();
    }

    public PageResult<SocFileIntegrityEvent> fileIntegrityEvents(SocPageRequest request) {
        LambdaQueryWrapper<SocFileIntegrityEvent> wrapper = scopedFileIntegrityWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocFileIntegrityEvent::getEventUid, request.keyword())
                        .or().like(SocFileIntegrityEvent::getHostname, request.keyword())
                        .or().like(SocFileIntegrityEvent::getAssetIp, request.keyword())
                        .or().like(SocFileIntegrityEvent::getFilePath, request.keyword()))
                .eq(notBlank(request.action()), SocFileIntegrityEvent::getAction, request.action())
                .eq(notBlank(request.severity()), SocFileIntegrityEvent::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocFileIntegrityEvent::getStatus, request.status())
                .orderByDesc(SocFileIntegrityEvent::getEventTime);
        return PageResult.from(fileIntegrityMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public SocFileIntegrityEvent fileIntegrityDetail(Long id) {
        SocFileIntegrityEvent item = fileIntegrityMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("文件完整性事件不存在");
        }
        ensureAccess(item.getOwnerId(), item.getDeptId(), "无权访问该文件完整性事件");
        return item;
    }

    @Transactional
    public SocFileIntegrityEvent updateFileIntegrityStatus(Long id, SocStatusRequest request) {
        SocFileIntegrityEvent item = fileIntegrityDetail(id);
        validateStatus(request.targetStatus(), "new", "reviewing", "confirmed", "ignored", "closed");
        item.setStatus(request.targetStatus());
        if ("confirmed".equals(request.targetStatus()) || "ignored".equals(request.targetStatus()) || "closed".equals(request.targetStatus())) {
            item.setReviewedAt(LocalDateTime.now());
        }
        fileIntegrityMapper.updateById(item);
        return item;
    }

    public List<NameValue> fileIntegritySummary() {
        return List.of("created", "modified", "deleted", "permission").stream()
                .map(action -> new NameValue(action, fileIntegrityMapper.selectCount(scopedFileIntegrityWrapper().eq(SocFileIntegrityEvent::getAction, action))))
                .toList();
    }

    public PageResult<SocExternalEvent> externalEvents(SocPageRequest request) {
        LambdaQueryWrapper<SocExternalEvent> wrapper = scopedExternalEventWrapper()
                .and(notBlank(request.keyword()), w -> w.like(SocExternalEvent::getEventUid, request.keyword())
                        .or().like(SocExternalEvent::getEventType, request.keyword())
                        .or().like(SocExternalEvent::getRuleId, request.keyword())
                        .or().like(SocExternalEvent::getRuleName, request.keyword())
                        .or().like(SocExternalEvent::getAssetName, request.keyword())
                        .or().like(SocExternalEvent::getAssetIp, request.keyword())
                        .or().like(SocExternalEvent::getSrcIp, request.keyword())
                        .or().like(SocExternalEvent::getDestIp, request.keyword())
                        .or().like(SocExternalEvent::getBatchId, request.keyword())
                        .or().like(SocExternalEvent::getCorrelationKey, request.keyword())
                        .or().like(SocExternalEvent::getIoc, request.keyword())
                        .or().like(SocExternalEvent::getRawEvent, request.keyword())
                        .or().like(SocExternalEvent::getNormalizedEvent, request.keyword()))
                .eq(notBlank(request.sourceType()), SocExternalEvent::getSourceType, request.sourceType())
                .eq(notBlank(request.eventType()), SocExternalEvent::getEventType, request.eventType())
                .eq(notBlank(request.severity()), SocExternalEvent::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocExternalEvent::getStatus, request.status())
                .orderByDesc(SocExternalEvent::getEventTime);
        return PageResult.from(externalEventMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public SocExternalEvent externalEventDetail(Long id) {
        SocExternalEvent item = externalEventMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("外部事件不存在");
        }
        ensureAccess(item.getOwnerId(), item.getDeptId(), "无权访问该外部事件");
        return item;
    }

    @Transactional
    public SocExternalEvent updateExternalEventStatus(Long id, SocStatusRequest request) {
        SocExternalEvent item = externalEventDetail(id);
        validateStatus(request.targetStatus(), "new", "reviewing", "linked", "ignored", "closed");
        item.setStatus(request.targetStatus());
        if ("linked".equals(request.targetStatus()) || "ignored".equals(request.targetStatus()) || "closed".equals(request.targetStatus())) {
            item.setReviewedAt(LocalDateTime.now());
        }
        externalEventMapper.updateById(item);
        return item;
    }

    public List<ExternalSourceSummary> externalEventSummary() {
        return List.of("waf", "zap", "trivy", "wazuh", "suricata", "zeek", "misp", "opencti", "sigma", "shuffle",
                        "falco", "osquery", "velociraptor", "cowrie").stream()
                .map(source -> {
                    LambdaQueryWrapper<SocExternalEvent> sourceWrapper = scopedExternalEventWrapper()
                            .eq(SocExternalEvent::getSourceType, source);
                    long total = externalEventMapper.selectCount(sourceWrapper);
                    long highRisk = externalEventMapper.selectCount(scopedExternalEventWrapper()
                            .eq(SocExternalEvent::getSourceType, source)
                            .in(SocExternalEvent::getSeverity, List.of("critical", "high")));
                    long linkedAlerts = externalEventMapper.selectCount(scopedExternalEventWrapper()
                            .eq(SocExternalEvent::getSourceType, source)
                            .isNotNull(SocExternalEvent::getAlertId));
                    return new ExternalSourceSummary(source, total, highRisk, linkedAlerts);
                })
                .filter(item -> item.total() > 0)
                .toList();
    }

    public PageResult<DetectionRuleSummary> detectionRules(SocPageRequest request) {
        Map<String, MutableDetectionRule> rules = new LinkedHashMap<>();
        detectionRuleCatalog().forEach(item -> rules.put(ruleKey(item.sourceType(), item.ruleId()),
                new MutableDetectionRule(item.ruleId(), item.ruleName(), item.sourceType(), item.severity(),
                        item.enabled(), item.version(), null, 0, 0)));

        List<SocExternalEvent> events = externalEventMapper.selectList(scopedExternalEventWrapper()
                .in(SocExternalEvent::getSourceType, detectionRuleSourceTypes())
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 1000"));
        for (SocExternalEvent event : events) {
            String sourceType = canonicalRuleSource(event.getSourceType());
            String ruleId = firstNotBlank(event.getRuleId(), event.getEventType(), sourceType.toUpperCase(Locale.ROOT));
            String key = ruleKey(sourceType, ruleId);
            MutableDetectionRule rule = rules.computeIfAbsent(key, ignored -> new MutableDetectionRule(ruleId,
                    firstNotBlank(event.getRuleName(), event.getEventType(), ruleId), sourceType,
                    normalizeSeverity(firstNotBlank(event.getSeverity(), "medium")), true, "live", null, 0, 0));
            rule.ruleName = firstNotBlank(event.getRuleName(), rule.ruleName);
            rule.severity = strongerSeverity(rule.severity, event.getSeverity());
            rule.hitCount++;
            rule.lastHitAt = latest(rule.lastHitAt, event.getEventTime());
        }

        List<SocAlert> alerts = alertMapper.selectList(scopedAlertWrapper()
                .in(SocAlert::getSourceType, detectionAlertSourceTypes())
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT 1000"));
        for (SocAlert alert : alerts) {
            String sourceType = canonicalRuleSource(alert.getSourceType());
            String ruleId = firstNotBlank(alert.getRuleId(), sourceType.toUpperCase(Locale.ROOT));
            String key = ruleKey(sourceType, ruleId);
            MutableDetectionRule rule = rules.computeIfAbsent(key, ignored -> new MutableDetectionRule(ruleId,
                    firstNotBlank(alert.getRuleDescription(), ruleId), sourceType,
                    normalizeSeverity(firstNotBlank(alert.getSeverity(), "medium")), true, "live", null, 0, 0));
            rule.ruleName = firstNotBlank(alert.getRuleDescription(), rule.ruleName);
            rule.severity = strongerSeverity(rule.severity, alert.getSeverity());
            rule.hitCount++;
            rule.lastHitAt = latest(rule.lastHitAt, alert.getEventTime());
            if ("false_positive".equals(alert.getStatus())) {
                rule.falsePositiveCount++;
            }
        }

        List<DetectionRuleSummary> filtered = rules.values().stream()
                .map(MutableDetectionRule::toSummary)
                .filter(item -> matchesRuleFilter(item, request))
                .sorted(Comparator
                        .comparing(DetectionRuleSummary::lastHitAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DetectionRuleSummary::sourceType)
                        .thenComparing(DetectionRuleSummary::ruleId))
                .toList();
        long pageNum = request.pageNum();
        long pageSize = request.pageSize();
        int from = (int) Math.min(filtered.size(), (pageNum - 1) * pageSize);
        int to = (int) Math.min(filtered.size(), from + pageSize);
        return new PageResult<>(filtered.subList(from, to), filtered.size(), pageNum, pageSize);
    }

    public DetectionRuleHits detectionRuleHits(String sourceType, String ruleId) {
        String canonicalSource = canonicalRuleSource(sourceType);
        String normalizedRuleId = trimToNull(ruleId);
        if (!notBlank(canonicalSource) || !notBlank(normalizedRuleId)) {
            throw new BusinessException("规则来源和规则 ID 不能为空");
        }
        List<String> eventSources = "wazuh".equals(canonicalSource) ? List.of("wazuh") : List.of(canonicalSource);
        List<String> alertSources = "wazuh".equals(canonicalSource) ? List.of("wazuh", "mock") : List.of(canonicalSource);
        List<SocExternalEvent> events = externalEventMapper.selectList(scopedExternalEventWrapper()
                .in(SocExternalEvent::getSourceType, eventSources)
                .eq(SocExternalEvent::getRuleId, normalizedRuleId)
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 20"));
        List<SocAlert> alerts = alertMapper.selectList(scopedAlertWrapper()
                .in(SocAlert::getSourceType, alertSources)
                .eq(SocAlert::getRuleId, normalizedRuleId)
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT 20"));
        alerts.forEach(this::enrichAlertEvidence);
        return new DetectionRuleHits(canonicalSource, normalizedRuleId, events, alerts);
    }

    public List<AdapterFieldMapping> adapterFieldMappings() {
        return List.of(
                new AdapterFieldMapping("waf", "ruleId/ruleName/action/targetUrl/httpStatus",
                        "ruleId, ruleName, action, targetUrl, severity", "ruleId and action required",
                        "block -> high by default; detect -> medium unless provided", "WAF-" + "{hash(requestId|demoCaseId|eventType)}",
                        "linkAlerts=true always links WAF demo evidence", "demo-data/waf-demo-events.jsonl",
                        "missing ruleId falls back to WAF-DEMO; invalid JSON line is skipped with an import error"),
                new AdapterFieldMapping("zap", "pluginid/name/riskdesc/url",
                        "ruleId=pluginid, ruleName=name, eventType=web_app_finding", "pluginid optional, name/url recommended",
                        "riskcode 3 or High -> high; 2 or Medium -> medium; otherwise low", "ZAP-" + "{hash(pluginid|name|url)}",
                        "linkAlerts=true links normalized web_app_finding events", "demo-data/demo-range/offline-batch.json",
                        "missing URL becomes web-target; malformed JSON is rejected for that line"),
                new AdapterFieldMapping("trivy", "VulnerabilityID/PkgName/InstalledVersion/FixedVersion/Severity",
                        "cveId, softwareName, softwareVersion, fixSuggestion, severity", "VulnerabilityID and PkgName recommended",
                        "Trivy Severity is normalized to critical/high/medium/low", "cveId + softwareName",
                        "Trivy writes soc_vulnerability, not soc_external_event; it does not create alerts directly", "demo-data/demo-range/offline-batch.json",
                        "empty Results/Vulnerabilities imports no vulnerability rows"),
                new AdapterFieldMapping("wazuh", "rule.id/rule.level/rule.description/agent.ip/data.srcip",
                        "ruleId, ruleName, severity, assetIp, srcIp", "rule.id optional, rule.description recommended",
                        "level >= 12 critical; >= 8 high; >= 4 medium; else low", "WAZUH-" + "{hash(id|raw)}",
                        "linkAlerts=true links xdr_alert events; seed mock alerts are shown as Wazuh rules", "demo-data/demo-range/offline-batch.json",
                        "missing agent.ip uses data dst fields or falls back to scoped owner/dept"),
                new AdapterFieldMapping("suricata", "alert.signature_id/alert.signature/alert.severity/src_ip/dest_ip",
                        "ruleId, ruleName, severity, srcIp, destIp, eventType", "alert object required for IDS alert mapping",
                        "severity 1 critical/high by signature context; lower values normalize to medium/low", "SURICATA-" + "{hash(timestamp|flow_id|signature)}",
                        "alert events link when linkAlerts=true; http/dns rows can remain evidence only", "Suricata eve.json",
                        "non-alert event_type is imported as http_anomaly/dns_activity without IDS signature"),
                new AdapterFieldMapping("zeek", "uid/id.orig_h/id.resp_h/proto/service/ts",
                        "ruleId=uid, ruleName=Zeek service/proto log, eventType=network_connection", "uid or connection tuple recommended",
                        "Zeek connection demo events default to medium", "ZEEK-" + "{hash(uid|line)}",
                        "linkAlerts=true links network_connection evidence", "Zeek conn.log or JSON",
                        "missing #fields header for TSV rows can cause line-level parse failure"),
                new AdapterFieldMapping("sigma", "id/title/name/tags/logsource",
                        "ruleId=id, ruleName=title/name, eventType=detection_rule", "id optional, title/name recommended",
                        "imported detection_rule metadata defaults to low", "SIGMA-" + "{hash(id|title)}",
                        "Sigma records are rule lifecycle metadata; alerting occurs when later events reuse the ruleId", "Sigma rule JSON/YAML converted to JSON",
                        "unsupported YAML must be converted to JSON before import")
        );
    }

    @Transactional
    public SuricataImportResult importSuricataEvents(SuricataImportRequest request) {
        String[] lines = request.content().split("\\R");
        int importedEvents = 0;
        int createdEvents = 0;
        int updatedEvents = 0;
        int linkedAlerts = 0;
        int skippedLines = 0;
        List<String> errors = new ArrayList<>();
        boolean linkAlerts = request.linkAlerts() == null || Boolean.TRUE.equals(request.linkAlerts());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                JsonNode raw = objectMapper.readTree(line);
                if (!raw.hasNonNull("event_type")) {
                    skippedLines++;
                    addImportError(errors, i + 1, "缺少 event_type");
                    continue;
                }
                NormalizedSuricataEvent normalized = normalizeSuricata(raw);
                if (!securityScope.canAccess(normalized.ownerId(), normalized.deptId())) {
                    skippedLines++;
                    addImportError(errors, i + 1, "无权导入该资产范围事件");
                    continue;
                }
                SocAlert linked = linkAlerts && normalized.alertEvent() ? upsertSuricataAlert(normalized) : null;
                if (linked != null) {
                    linkedAlerts++;
                }
                UpsertResult eventResult = upsertExternalEvent(normalized, linked == null ? null : linked.getId());
                importedEvents++;
                if (eventResult.created()) {
                    createdEvents++;
                } else {
                    updatedEvents++;
                }
            } catch (JsonProcessingException ex) {
                skippedLines++;
                addImportError(errors, i + 1, "JSON 格式错误");
            }
        }
        if (importedEvents == 0 && skippedLines == 0) {
            throw new BusinessException("未发现可导入的 Suricata 事件");
        }
        return new SuricataImportResult(importedEvents, createdEvents, updatedEvents, linkedAlerts, skippedLines, errors);
    }

    @Transactional
    public CyberFusionImportResult importCyberFusionEvents(CyberFusionImportRequest request) {
        String sourceType = request.sourceType().toLowerCase(Locale.ROOT);
        if ("suricata".equals(sourceType) && !hasConfiguredEventAdapter(sourceType)) {
            SuricataImportResult result = importSuricataEvents(new SuricataImportRequest(request.content(), request.linkAlerts()));
            return new CyberFusionImportResult(sourceType, result.importedEvents(), result.createdEvents(),
                    result.updatedEvents(), result.linkedAlerts(), 0, result.skippedLines(), result.errors());
        }
        if ("trivy".equals(sourceType)) {
            return importTrivyVulnerabilities(request.content());
        }

        int importedEvents = 0;
        int createdEvents = 0;
        int updatedEvents = 0;
        int linkedAlerts = 0;
        int skippedLines = 0;
        List<String> errors = new ArrayList<>();
        boolean linkAlerts = request.linkAlerts() == null || Boolean.TRUE.equals(request.linkAlerts());

        String[] zeekFields = new String[0];
        List<String> lines = cyberFusionImportLines(request.content(), sourceType);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("zeek".equals(sourceType) && line.startsWith("#fields")) {
                zeekFields = line.replaceFirst("^#fields\\s+", "").split("\\t");
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            try {
                List<NormalizedCyberFusionEvent> normalizedEvents = normalizeCyberFusionLine(sourceType, line, zeekFields);
                if (normalizedEvents.isEmpty()) {
                    skippedLines++;
                    addImportError(errors, i + 1, "未识别可导入记录");
                    continue;
                }
                for (NormalizedCyberFusionEvent normalized : normalizedEvents) {
                    if (!securityScope.canAccess(normalized.ownerId(), normalized.deptId())) {
                        skippedLines++;
                        addImportError(errors, i + 1, "无权导入该资产范围事件");
                        continue;
                    }
                    SocAlert linked = linkAlerts && normalized.alertEvent() ? upsertCyberFusionAlert(normalized) : null;
                    if (linked != null) {
                        linkedAlerts++;
                    }
                    UpsertResult eventResult = upsertExternalEvent(normalized, linked == null ? null : linked.getId());
                    importedEvents++;
                    if (eventResult.created()) {
                        createdEvents++;
                    } else {
                        updatedEvents++;
                    }
                }
            } catch (JsonProcessingException ex) {
                skippedLines++;
                addImportError(errors, i + 1, "JSON 格式错误");
            }
        }
        if (importedEvents == 0 && skippedLines == 0) {
            throw new BusinessException("未发现可导入的 " + sourceType + " 记录");
        }
        return new CyberFusionImportResult(sourceType, importedEvents, createdEvents, updatedEvents, linkedAlerts, 0, skippedLines, errors);
    }

    private List<String> cyberFusionImportLines(String content, String sourceType) {
        String trimmed = content.trim();
        if (trimmed.startsWith("{") && content.lines().filter(line -> !line.trim().isEmpty()).count() > 1) {
            return Arrays.asList(content.split("\\R"));
        }
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                return jsonRecords(content, sourceType);
            } catch (BusinessException ex) {
                if (!trimmed.startsWith("{")) {
                    throw ex;
                }
            }
        }
        return Arrays.asList(content.split("\\R"));
    }

    @Transactional
    public DemoDataOperationResult importDemoData() {
        int deletedRows = clearDemoDataRows();
        runClasspathSql(DEMO_DATA_IMPORT_SCRIPT);
        DemoRangeBatchImportResult rangeResult = importDemoRangeBatch(new DemoRangeBatchImportRequest(DEFAULT_DEMO_RANGE_BATCH_ID, true));
        int totalRows = countDemoDataRows();
        return new DemoDataOperationResult(
                DEMO_SEED_BATCH_ID,
                rangeResult.batchId(),
                totalRows,
                deletedRows,
                rangeResult.importedEvents(),
                rangeResult.createdAlerts(),
                rangeResult.createdVulnerabilities(),
                rangeResult.updatedEvents(),
                "演示数据已导入：启动默认数据保持为空，本次仅写入手动演示批次和离线证据链。",
                rangeResult.errors()
        );
    }

    @Transactional
    public DemoDataOperationResult clearDemoData() {
        int deletedRows = clearDemoDataRows();
        return new DemoDataOperationResult(
                DEMO_SEED_BATCH_ID,
                DEFAULT_DEMO_RANGE_BATCH_ID,
                countDemoDataRows(),
                deletedRows,
                0,
                0,
                0,
                0,
                deletedRows == 0 ? "当前没有可清除的演示数据。" : "演示数据已清除，真实本机数据保留。",
                List.of()
        );
    }

    @Transactional
    public DemoDataOperationResult clearHostAgentSmokeData(String batchId) {
        String normalizedBatchId = normalizeHostAgentSmokeBatchId(batchId);
        int deletedRows = clearHostAgentSmokeRows(normalizedBatchId);
        return new DemoDataOperationResult(
                DEMO_SEED_BATCH_ID,
                DEFAULT_DEMO_RANGE_BATCH_ID,
                countDemoDataRows(),
                deletedRows,
                0,
                0,
                0,
                0,
                deletedRows == 0 ? "当前没有 Host Agent 验收 fixture 数据。" : "Host Agent 验收 fixture 数据已清理，真实主机数据保留。",
                List.of()
        );
    }

    public DemoDataStatus demoDataStatus() {
        int totalRows = countDemoDataRows();
        return new DemoDataStatus(
                DEMO_SEED_BATCH_ID,
                DEFAULT_DEMO_RANGE_BATCH_ID,
                totalRows,
                totalRows > 0,
                totalRows > 0 ? "当前存在演示数据，可通过左侧按钮清除。" : "当前无演示数据，页面会展示本机真实数据。"
        );
    }

    private int clearDemoDataRows() {
        int rowsBeforeClear = countDemoDataRows();
        runClasspathSql(DEMO_DATA_CLEAR_SCRIPT);
        clearHostAgentSmokeRows(null);
        clearHostAgentFixtureRows();
        return rowsBeforeClear;
    }

    private int clearHostAgentFixtureRows() {
        JdbcTemplate jdbc = demoJdbc();
        int rows = 0;
        rows += jdbc.update("""
                DELETE FROM soc_playbook_match_log
                WHERE alert_id IN (
                  SELECT id FROM soc_alert
                  WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                    AND (
                      asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                      OR asset_ip LIKE '192.0.2.%'
                      OR asset_ip LIKE '198.18.%'
                      OR asset_ip LIKE '198.19.%'
                      OR alert_uid LIKE '%FIXTURE%'
                      OR raw_ref LIKE '%fixture%'
                      OR rule_description LIKE '%fixture%'
                      OR rule_description LIKE '%Fixture%'
                    )
                )
                   OR ticket_id IN (
                     SELECT id FROM soc_ticket
                     WHERE title LIKE '%mac-dev-host%'
                        OR title LIKE '%win-docker-host%'
                        OR title LIKE '%mac-incident-host%'
                        OR title LIKE '%win-incident-host%'
                        OR title LIKE '%192.0.2.%'
                        OR title LIKE '%198.18.%'
                        OR title LIKE '%198.19.%'
                   )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_ticket_task
                WHERE ticket_id IN (
                  SELECT id FROM soc_ticket
                  WHERE title LIKE '%mac-dev-host%'
                     OR title LIKE '%win-docker-host%'
                     OR title LIKE '%mac-incident-host%'
                     OR title LIKE '%win-incident-host%'
                     OR title LIKE '%192.0.2.%'
                     OR title LIKE '%198.18.%'
                     OR title LIKE '%198.19.%'
                )
                   OR alert_id IN (
                     SELECT id FROM soc_alert
                     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                       AND (
                         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                         OR asset_ip LIKE '192.0.2.%'
                         OR asset_ip LIKE '198.18.%'
                         OR asset_ip LIKE '198.19.%'
                       )
                   )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_ticket_timeline
                WHERE ticket_id IN (
                  SELECT id FROM soc_ticket
                  WHERE title LIKE '%mac-dev-host%'
                     OR title LIKE '%win-docker-host%'
                     OR title LIKE '%mac-incident-host%'
                     OR title LIKE '%win-incident-host%'
                     OR title LIKE '%192.0.2.%'
                     OR title LIKE '%198.18.%'
                     OR title LIKE '%198.19.%'
                )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_incident_evidence
                WHERE asset_ip LIKE '192.0.2.%'
                   OR asset_ip LIKE '198.18.%'
                   OR asset_ip LIKE '198.19.%'
                   OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                   OR evidence_uid LIKE '%FIXTURE%'
                   OR cluster_id IN (
                     SELECT id FROM soc_incident_cluster
                     WHERE hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                        OR primary_hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                        OR asset_ip LIKE '192.0.2.%'
                        OR primary_asset_ip LIKE '192.0.2.%'
                        OR asset_ip LIKE '198.18.%'
                        OR primary_asset_ip LIKE '198.18.%'
                        OR asset_ip LIKE '198.19.%'
                        OR primary_asset_ip LIKE '198.19.%'
                        OR title LIKE '%fixture%'
                        OR title LIKE '%Fixture%'
                        OR summary LIKE '%fixture%'
                   )
                   OR (evidence_type = 'external_event' AND evidence_id IN (
                     SELECT id FROM soc_external_event
                     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                       AND (
                         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                         OR asset_ip LIKE '192.0.2.%'
                         OR asset_ip LIKE '198.18.%'
                         OR asset_ip LIKE '198.19.%'
                         OR event_uid LIKE '%FIXTURE%'
                         OR CAST(raw_event AS CHAR) LIKE '%"fixture": true%'
                         OR CAST(normalized_event AS CHAR) LIKE '%"fixture": true%'
                       )
                   ))
                   OR (evidence_type = 'alert' AND evidence_id IN (
                     SELECT id FROM soc_alert
                     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                       AND (
                         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                         OR asset_ip LIKE '192.0.2.%'
                         OR asset_ip LIKE '198.18.%'
                         OR asset_ip LIKE '198.19.%'
                         OR alert_uid LIKE '%FIXTURE%'
                       )
                   ))
                """);
        rows += jdbc.update("""
                DELETE FROM soc_ticket
                WHERE title LIKE '%mac-dev-host%'
                   OR title LIKE '%win-docker-host%'
                   OR title LIKE '%mac-incident-host%'
                   OR title LIKE '%win-incident-host%'
                   OR title LIKE '%192.0.2.%'
                   OR title LIKE '%198.18.%'
                   OR title LIKE '%198.19.%'
                   OR alert_id IN (
                     SELECT id FROM soc_alert
                     WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                       AND (
                         asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                         OR asset_ip LIKE '192.0.2.%'
                         OR asset_ip LIKE '198.18.%'
                         OR asset_ip LIKE '198.19.%'
                       )
                   )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_incident_cluster
                WHERE hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                   OR primary_hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                   OR asset_ip LIKE '192.0.2.%'
                   OR primary_asset_ip LIKE '192.0.2.%'
                   OR asset_ip LIKE '198.18.%'
                   OR primary_asset_ip LIKE '198.18.%'
                   OR asset_ip LIKE '198.19.%'
                   OR primary_asset_ip LIKE '198.19.%'
                   OR title LIKE '%fixture%'
                   OR title LIKE '%Fixture%'
                   OR summary LIKE '%fixture%'
                """);
        rows += jdbc.update("""
                DELETE factor FROM soc_asset_risk_factor factor
                JOIN soc_asset_risk_snapshot snapshot ON factor.snapshot_id = snapshot.id
                WHERE snapshot.asset_ip LIKE '192.0.2.%'
                   OR snapshot.asset_ip LIKE '198.18.%'
                   OR snapshot.asset_ip LIKE '198.19.%'
                   OR snapshot.hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                """);
        rows += jdbc.update("""
                DELETE FROM soc_asset_risk_snapshot
                WHERE asset_ip LIKE '192.0.2.%'
                   OR asset_ip LIKE '198.18.%'
                   OR asset_ip LIKE '198.19.%'
                   OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                """);
        rows += jdbc.update("""
                DELETE FROM soc_external_event
                WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                  AND (
                    asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                    OR asset_ip LIKE '192.0.2.%'
                    OR asset_ip LIKE '198.18.%'
                    OR asset_ip LIKE '198.19.%'
                    OR batch_id LIKE 'HOST-%fixture-agent-%'
                    OR event_uid LIKE '%FIXTURE%'
                    OR CAST(raw_event AS CHAR) LIKE '%"fixture": true%'
                    OR CAST(normalized_event AS CHAR) LIKE '%"fixture": true%'
                  )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_alert
                WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                  AND (
                    asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                    OR asset_ip LIKE '192.0.2.%'
                    OR asset_ip LIKE '198.18.%'
                    OR asset_ip LIKE '198.19.%'
                    OR batch_id LIKE 'HOST-%fixture-agent-%'
                    OR alert_uid LIKE '%FIXTURE%'
                    OR raw_ref LIKE '%fixture%'
                    OR rule_description LIKE '%fixture%'
                    OR rule_description LIKE '%Fixture%'
                    OR evidence_summary LIKE '%fixture%'
                  )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_file_integrity_event
                WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                  AND (
                    hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                    OR asset_ip LIKE '192.0.2.%'
                    OR asset_ip LIKE '198.18.%'
                    OR asset_ip LIKE '198.19.%'
                    OR event_uid LIKE '%FIXTURE%'
                    OR rule_name LIKE '%Fixture%'
                    OR rule_name LIKE '%fixture%'
                  )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_baseline_check
                WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                  AND (
                    asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                    OR asset_ip LIKE '192.0.2.%'
                    OR asset_ip LIKE '198.18.%'
                    OR asset_ip LIKE '198.19.%'
                    OR check_code LIKE '%fixture%'
                    OR check_code LIKE '%FIXTURE%'
                    OR check_item LIKE '%Fixture%'
                    OR check_item LIKE '%fixture%'
                  )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_asset
                WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                  AND (
                    hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                    OR ip LIKE '192.0.2.%'
                    OR ip LIKE '198.18.%'
                    OR ip LIKE '198.19.%'
                  )
                """);
        rows += jdbc.update("""
                DELETE FROM soc_ingest_reject_log
	                WHERE agent_id LIKE '%fixture-agent%'
	                   OR agent_id LIKE 'queue-replay-macos-agent-%'
	                   OR agent_id LIKE 'queue-pressure-macos-agent-%'
	                   OR batch_id LIKE 'HOST-%fixture-agent-%'
	                """);
        rows += jdbc.update("""
                DELETE FROM soc_ingest_batch
	                WHERE agent_id LIKE '%fixture-agent%'
	                   OR agent_id LIKE 'queue-replay-macos-agent-%'
	                   OR agent_id LIKE 'queue-pressure-macos-agent-%'
	                   OR batch_id LIKE 'HOST-%fixture-agent-%'
	                """);
        rows += jdbc.update("""
                DELETE FROM soc_host_agent
	                WHERE agent_id LIKE '%fixture-agent%'
	                   OR agent_id LIKE 'queue-replay-macos-agent-%'
	                   OR agent_id LIKE 'queue-pressure-macos-agent-%'
	                   OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                   OR CAST(ip_addresses_json AS CHAR) LIKE '%192.0.2.%'
                   OR CAST(ip_addresses_json AS CHAR) LIKE '%198.18.%'
                   OR CAST(ip_addresses_json AS CHAR) LIKE '%198.19.%'
                   OR CAST(labels_json AS CHAR) LIKE '%"fixture": "true"%'
	                   OR CAST(labels_json AS CHAR) LIKE '%"fixture":"true"%'
	                   OR CAST(labels_json AS CHAR) LIKE '%queue-replay%'
	                   OR CAST(labels_json AS CHAR) LIKE '%queue-pressure%'
	                """);
        return rows;
    }

    private int clearHostAgentSmokeRows(String batchId) {
        JdbcTemplate jdbc = demoJdbc();
        String batchLike = notBlank(batchId) ? batchId + "%" : HOST_AGENT_INCIDENT_SMOKE_PREFIX + "%";
        String containsLike = notBlank(batchId) ? "%" + batchId + "%" : "%" + HOST_AGENT_INCIDENT_SMOKE_PREFIX + "%";
        int rows = 0;
        rows += jdbc.update("""
                DELETE FROM soc_playbook_match_log
                WHERE alert_id IN (
                  SELECT id FROM soc_alert
                  WHERE batch_id LIKE ? OR raw_ref LIKE ? OR rule_description LIKE ?
                )
                   OR ticket_id IN (
                     SELECT id FROM soc_ticket
                     WHERE alert_id IN (
                       SELECT id FROM soc_alert
                       WHERE batch_id LIKE ? OR raw_ref LIKE ? OR rule_description LIKE ?
                     )
                        OR id IN (
                          SELECT ticket_id FROM soc_incident_cluster
                          WHERE ticket_id IS NOT NULL
                            AND (batch_id LIKE ? OR correlation_key LIKE ? OR title LIKE ? OR summary LIKE ?)
                        )
                        OR title LIKE '%mac-incident-host%'
                        OR title LIKE '%win-incident-host%'
                        OR title LIKE '%198.18.%'
                        OR title LIKE '%198.19.%'
                   )
                """, batchLike, containsLike, containsLike, batchLike, containsLike, containsLike,
                batchLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_ticket_task
                WHERE ticket_id IN (
                  SELECT id FROM soc_ticket
                  WHERE alert_id IN (
                    SELECT id FROM soc_alert
                    WHERE batch_id LIKE ? OR raw_ref LIKE ? OR rule_description LIKE ?
                  )
                     OR id IN (
                       SELECT ticket_id FROM soc_incident_cluster
                       WHERE ticket_id IS NOT NULL
                         AND (batch_id LIKE ? OR correlation_key LIKE ? OR title LIKE ? OR summary LIKE ?)
                     )
                     OR title LIKE '%mac-incident-host%'
                     OR title LIKE '%win-incident-host%'
                     OR title LIKE '%198.18.%'
                     OR title LIKE '%198.19.%'
                )
                """, batchLike, containsLike, containsLike, batchLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_ticket_timeline
                WHERE ticket_id IN (
                  SELECT id FROM soc_ticket
                  WHERE alert_id IN (
                    SELECT id FROM soc_alert
                    WHERE batch_id LIKE ? OR raw_ref LIKE ? OR rule_description LIKE ?
                  )
                     OR id IN (
                       SELECT ticket_id FROM soc_incident_cluster
                       WHERE ticket_id IS NOT NULL
                         AND (batch_id LIKE ? OR correlation_key LIKE ? OR title LIKE ? OR summary LIKE ?)
                     )
                     OR title LIKE '%mac-incident-host%'
                     OR title LIKE '%win-incident-host%'
                     OR title LIKE '%198.18.%'
                     OR title LIKE '%198.19.%'
                )
                """, batchLike, containsLike, containsLike, batchLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_incident_evidence
                WHERE batch_id LIKE ?
                   OR evidence_uid LIKE ?
                   OR cluster_id IN (
                     SELECT id FROM soc_incident_cluster
                     WHERE batch_id LIKE ? OR correlation_key LIKE ? OR title LIKE ? OR summary LIKE ?
                   )
                   OR (evidence_type = 'external_event' AND evidence_id IN (
                     SELECT id FROM soc_external_event
                     WHERE batch_id LIKE ? OR event_uid LIKE ? OR CAST(raw_event AS CHAR) LIKE ? OR CAST(normalized_event AS CHAR) LIKE ?
                   ))
                   OR (evidence_type = 'alert' AND evidence_id IN (
                     SELECT id FROM soc_alert
                     WHERE batch_id LIKE ? OR alert_uid LIKE ? OR raw_ref LIKE ? OR rule_description LIKE ?
                   ))
                """, batchLike, containsLike, batchLike, containsLike, containsLike, containsLike,
                batchLike, containsLike, containsLike, containsLike,
                batchLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_ticket
                WHERE alert_id IN (
                  SELECT id FROM soc_alert
                  WHERE batch_id LIKE ? OR raw_ref LIKE ? OR rule_description LIKE ?
                )
                   OR id IN (
                     SELECT ticket_id FROM soc_incident_cluster
                     WHERE ticket_id IS NOT NULL
                       AND (batch_id LIKE ? OR correlation_key LIKE ? OR title LIKE ? OR summary LIKE ?)
                   )
                   OR title LIKE ?
                   OR title LIKE '%mac-incident-host%'
                   OR title LIKE '%win-incident-host%'
                   OR title LIKE '%198.18.%'
                   OR title LIKE '%198.19.%'
                """, batchLike, containsLike, containsLike, batchLike, containsLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_incident_cluster
                WHERE batch_id LIKE ?
                   OR correlation_key LIKE ?
                   OR title LIKE ?
                   OR summary LIKE ?
                """, batchLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_external_event
                WHERE batch_id LIKE ?
                   OR event_uid LIKE ?
                   OR CAST(raw_event AS CHAR) LIKE ?
                   OR CAST(normalized_event AS CHAR) LIKE ?
                """, batchLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_alert
                WHERE batch_id LIKE ?
                   OR alert_uid LIKE ?
                   OR raw_ref LIKE ?
                   OR rule_description LIKE ?
                   OR evidence_summary LIKE ?
                """, batchLike, containsLike, containsLike, containsLike, containsLike);
        rows += jdbc.update("""
                DELETE FROM soc_asset
                WHERE source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                  AND hostname IN ('mac-incident-host', 'win-incident-host')
                  AND (ip LIKE '198.18.%' OR ip LIKE '198.19.%')
                """);
        rows += jdbc.update("""
                DELETE FROM soc_ingest_reject_log
                WHERE batch_id LIKE ?
                   OR agent_id IN ('incident-macos-agent', 'incident-windows-agent')
                """, batchLike);
        rows += jdbc.update("""
                DELETE FROM soc_ingest_batch
                WHERE batch_id LIKE ?
                   OR agent_id IN ('incident-macos-agent', 'incident-windows-agent')
                """, batchLike);
        rows += jdbc.update("""
                DELETE FROM soc_host_agent
                WHERE agent_id IN ('incident-macos-agent', 'incident-windows-agent')
                   OR CAST(labels_json AS CHAR) LIKE '%"scope": "incident-chain"%'
                   OR CAST(labels_json AS CHAR) LIKE '%"scope":"incident-chain"%'
                """);
        return rows;
    }

    private String normalizeHostAgentSmokeBatchId(String batchId) {
        if (!notBlank(batchId)) {
            throw new BusinessException("Host Agent smoke batchId is required");
        }
        String normalized = batchId.trim();
        if (!normalized.startsWith(HOST_AGENT_INCIDENT_SMOKE_PREFIX) || !normalized.matches("[A-Za-z0-9.:-]+")) {
            throw new BusinessException("只能清理 HOST-AGENT-INCIDENT-SMOKE-* 验收批次");
        }
        return normalized;
    }

    private int countDemoDataRows() {
        Integer count = demoJdbc().queryForObject("""
                SELECT COALESCE(SUM(cnt), 0)
                FROM (
                  SELECT COUNT(*) cnt FROM soc_asset
                    WHERE source_type IN ('demo', 'mock', 'local-demo-client')
                       OR (ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
                           AND hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'))
                       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                           AND (hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                                OR ip LIKE '192.0.2.%'
                                OR ip LIKE '198.18.%'
                                OR ip LIKE '198.19.%'))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_alert
                    WHERE batch_id LIKE 'DEMO-%'
                       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
                       OR demo_case_id IS NOT NULL
                       OR source_type IN ('demo', 'mock', 'local-demo-client')
                       OR alert_uid IN ('MOCK-20260527-0001','MOCK-20260527-0002','MOCK-20260526-0003','MOCK-20260525-0004',
                                        'MOCK-20260527-0005','SURICATA-20260527-0001','SURICATA-20260527-0002')
                       OR raw_ref LIKE '%DEMO-%'
                       OR raw_ref LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
                       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                           AND (asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                                OR asset_ip LIKE '192.0.2.%'
                                OR asset_ip LIKE '198.18.%'
                                OR asset_ip LIKE '198.19.%'
                                OR batch_id LIKE 'HOST-%fixture-agent-%'
                                OR alert_uid LIKE '%FIXTURE%'
                                OR raw_ref LIKE '%fixture%'
                                OR rule_description LIKE '%fixture%'
                                OR rule_description LIKE '%Fixture%'
                                OR evidence_summary LIKE '%fixture%'))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_external_event
                    WHERE batch_id LIKE 'DEMO-%'
                       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
                       OR demo_case_id IS NOT NULL
                       OR source_type IN ('demo', 'mock', 'local-demo-client')
                       OR event_uid IN ('EXT-SURICATA-20260527-0001','EXT-SURICATA-20260527-0002')
                       OR event_uid LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
                       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                           AND (asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                                OR asset_ip LIKE '192.0.2.%'
                                OR asset_ip LIKE '198.18.%'
                                OR asset_ip LIKE '198.19.%'
                                OR batch_id LIKE 'HOST-%fixture-agent-%'
                                OR event_uid LIKE '%FIXTURE%'
                                OR CAST(raw_event AS CHAR) LIKE '%"fixture": true%'
                                OR CAST(normalized_event AS CHAR) LIKE '%"fixture": true%'))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_ticket
                    WHERE ticket_no IN ('INC-202605260001','INC-202605250001')
                       OR title LIKE '%DEMO-%'
                       OR title LIKE '%演示数据%'
                       OR title LIKE '%mac-dev-host%'
                       OR title LIKE '%win-docker-host%'
                       OR title LIKE '%mac-incident-host%'
                       OR title LIKE '%win-incident-host%'
                       OR title LIKE '%192.0.2.%'
                       OR title LIKE '%198.18.%'
                       OR title LIKE '%198.19.%'
                  UNION ALL
                  SELECT COUNT(*) FROM soc_report
                    WHERE report_no = 'RPT-DAILY-202605270001'
                       OR report_no LIKE 'RPT-VALIDATION-%'
                       OR title LIKE '%DEMO-%'
                       OR summary LIKE '%DEMO-%'
                  UNION ALL
                  SELECT COUNT(*) FROM soc_notification_log
                    WHERE event_type = 'demo_range_batch_imported'
                       OR title LIKE '%DEMO-%'
                       OR content LIKE '%DEMO-%'
                       OR content LIKE '%演示数据%'
                       OR (title = '高危告警已转工单'
                           AND content = '关键系统配置文件发生变更，已进入工单处置流程。'
                           AND target = 'soc-team@example.local')
                  UNION ALL
                  SELECT COUNT(*) FROM soc_alert_whitelist
                    WHERE (asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
                           AND (reason LIKE '%演示%' OR rule_id IN ('530','5502','WAF-DEMO-1001','WAF-DEMO-2001')))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_vulnerability
                    WHERE source_type IN ('demo', 'mock')
                       OR cve_id LIKE 'CVE-2026-DEMO-RANGE-%'
                       OR (cve_id IN ('CVE-2024-3094','CVE-2023-38408','CVE-2024-6387','CVE-2022-22965')
                           AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_baseline_check
                    WHERE source_type IN ('demo', 'mock')
                       OR (check_code IN ('SSH_ROOT_LOGIN','PASSWORD_MAX_DAYS','FIREWALL_DEFAULT_DENY','SENSITIVE_FILE_PERMISSION','UNNEEDED_SERVICE')
                           AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'))
                       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                           AND (asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                                OR asset_ip LIKE '192.0.2.%'
                                OR asset_ip LIKE '198.18.%'
                                OR asset_ip LIKE '198.19.%'
                                OR check_code LIKE '%fixture%'
                                OR check_code LIKE '%FIXTURE%'
                                OR check_item LIKE '%Fixture%'
                                OR check_item LIKE '%fixture%'))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_file_integrity_event
                    WHERE source_type IN ('demo', 'mock')
                       OR event_uid IN ('FIM-20260527-0001','FIM-20260527-0002','FIM-20260526-0003','FIM-20260525-0004')
                       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
                           AND (hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                                OR asset_ip LIKE '192.0.2.%'
                                OR asset_ip LIKE '198.18.%'
                                OR asset_ip LIKE '198.19.%'
                                OR event_uid LIKE '%FIXTURE%'
                                OR rule_name LIKE '%Fixture%'
                                OR rule_name LIKE '%fixture%'))
                  UNION ALL
                  SELECT COUNT(*) FROM soc_incident_cluster
                    WHERE batch_id LIKE 'DEMO-%'
                       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
                       OR demo_case_id IS NOT NULL
                       OR correlation_key LIKE '%DEMO-%'
                       OR correlation_key LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
                       OR title LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
                       OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                       OR primary_hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                       OR asset_ip LIKE '192.0.2.%'
                       OR primary_asset_ip LIKE '192.0.2.%'
                       OR asset_ip LIKE '198.18.%'
                       OR primary_asset_ip LIKE '198.18.%'
                       OR asset_ip LIKE '198.19.%'
                       OR primary_asset_ip LIKE '198.19.%'
                       OR title LIKE '%fixture%'
                       OR title LIKE '%Fixture%'
                  UNION ALL
                  SELECT COUNT(*) FROM soc_incident_evidence
                    WHERE batch_id LIKE 'DEMO-%'
                       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
                       OR demo_case_id IS NOT NULL
                       OR evidence_uid LIKE '%DEMO-%'
                       OR evidence_uid LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
                       OR evidence_uid LIKE '%FIXTURE%'
                       OR asset_ip LIKE '192.0.2.%'
                       OR asset_ip LIKE '198.18.%'
                       OR asset_ip LIKE '198.19.%'
                       OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                  UNION ALL
                  SELECT COUNT(*) FROM soc_algorithm_evaluation
                    WHERE batch_id LIKE 'DEMO-%'
                  UNION ALL
                  SELECT COUNT(*) FROM soc_host_agent
                    WHERE agent_id IN ('incident-macos-agent', 'incident-windows-agent')
	                       OR agent_id LIKE '%fixture-agent%'
	                       OR agent_id LIKE 'queue-replay-macos-agent-%'
	                       OR agent_id LIKE 'queue-pressure-macos-agent-%'
	                       OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                       OR CAST(ip_addresses_json AS CHAR) LIKE '%192.0.2.%'
                       OR CAST(ip_addresses_json AS CHAR) LIKE '%198.18.%'
                       OR CAST(ip_addresses_json AS CHAR) LIKE '%198.19.%'
                       OR CAST(labels_json AS CHAR) LIKE '%"fixture": "true"%'
                       OR CAST(labels_json AS CHAR) LIKE '%"fixture":"true"%'
	                       OR CAST(labels_json AS CHAR) LIKE '%incident-chain%'
	                       OR CAST(labels_json AS CHAR) LIKE '%queue-replay%'
	                       OR CAST(labels_json AS CHAR) LIKE '%queue-pressure%'
	                  UNION ALL
                  SELECT COUNT(*) FROM soc_ingest_batch
                    WHERE batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
	                       OR agent_id IN ('incident-macos-agent', 'incident-windows-agent')
	                       OR agent_id LIKE '%fixture-agent%'
	                       OR agent_id LIKE 'queue-replay-macos-agent-%'
	                       OR agent_id LIKE 'queue-pressure-macos-agent-%'
	                       OR batch_id LIKE 'HOST-%fixture-agent-%'
                  UNION ALL
                  SELECT COUNT(*) FROM soc_ingest_reject_log
                    WHERE batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
	                       OR agent_id IN ('incident-macos-agent', 'incident-windows-agent')
	                       OR agent_id LIKE '%fixture-agent%'
	                       OR agent_id LIKE 'queue-replay-macos-agent-%'
	                       OR agent_id LIKE 'queue-pressure-macos-agent-%'
	                       OR batch_id LIKE 'HOST-%fixture-agent-%'
                ) AS demo_counts
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private JdbcTemplate demoJdbc() {
        if (jdbcTemplate == null || jdbcTemplate.getDataSource() == null) {
            throw new BusinessException("演示数据管理需要可用的数据库连接");
        }
        return jdbcTemplate;
    }

    private void runClasspathSql(String location) {
        DataSource dataSource = Objects.requireNonNull(demoJdbc().getDataSource());
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(location));
        } catch (RuntimeException ex) {
            throw new BusinessException("执行演示数据脚本失败：" + limit(firstNotBlank(ex.getMessage(), location), 220));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Transactional
    public DemoRangeBatchImportResult importDemoRangeBatch(DemoRangeBatchImportRequest request) {
        String batchId = normalizeDemoBatchId(request == null ? null : request.batchId());
        boolean linkAlerts = request == null || request.linkAlerts() == null || Boolean.TRUE.equals(request.linkAlerts());
        int importedEvents = 0;
        int updatedEvents = 0;
        int createdAlerts = 0;
        int createdVulnerabilities = 0;
        int skippedItems = 0;
        int failedItems = 0;
        List<DemoRangeSourceImportResult> sources = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (DemoRangeSourceSample sample : DEMO_RANGE_SOURCE_SAMPLES) {
            try {
                CyberFusionImportResult result = importCyberFusionEvents(new CyberFusionImportRequest(
                        sample.sourceType(),
                        sample.content().replace("{{batchId}}", batchId),
                        linkAlerts
                ));
                importedEvents += result.importedEvents();
                updatedEvents += result.updatedEvents();
                createdAlerts += result.linkedAlerts();
                createdVulnerabilities += result.importedVulnerabilities();
                skippedItems += result.skippedLines();
                failedItems += result.errors().size();
                sources.add(new DemoRangeSourceImportResult(sample.sourceType(), result.importedEvents(),
                        result.createdEvents(), result.updatedEvents(), result.linkedAlerts(),
                        result.importedVulnerabilities(), result.skippedLines(), result.errors()));
            } catch (RuntimeException ex) {
                failedItems++;
                String message = sample.sourceType() + ": " + limit(firstNotBlank(ex.getMessage(), "导入失败"), 180);
                errors.add(message);
                sources.add(new DemoRangeSourceImportResult(sample.sourceType(), 0, 0, 0, 0, 0, 0, List.of(message)));
            }
        }

        dispatchNotification("demo_range_batch_imported", "medium", "demo_range", null,
                "Demo Range 批次已导入：" + batchId,
                "批次 " + batchId + " 已完成离线导入，事件 " + importedEvents + " 条，联动告警 " + createdAlerts
                        + " 条，漏洞 " + createdVulnerabilities + " 条；通知为 dry-run 留痕。");

        return new DemoRangeBatchImportResult(batchId, importedEvents, createdAlerts, createdVulnerabilities,
                skippedItems, failedItems, updatedEvents, sources,
                "固定离线样例使用稳定 batchId、requestId、event_uid/alert_uid 和 CVE+软件名执行 upsert；重复导入会刷新同一批次证据，不会无限追加重复记录。",
                errors);
    }

    public DemoRangeEvidenceChain demoRangeEvidenceChain(String requestedBatchId) {
        String batchId = normalizeDemoBatchId(requestedBatchId);
        List<SocExternalEvent> events = externalEventMapper.selectList(scopedExternalEventWrapper()
                .and(w -> w.like(SocExternalEvent::getRawEvent, batchId)
                        .or().like(SocExternalEvent::getNormalizedEvent, batchId)
                        .or().like(SocExternalEvent::getEventUid, batchId))
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 100"));
        List<SocAlert> alerts = alertMapper.selectList(scopedAlertWrapper()
                .and(w -> w.like(SocAlert::getRawRef, batchId)
                        .or().like(SocAlert::getAlertUid, batchId)
                        .or().like(SocAlert::getRuleId, batchId)
                        .or().like(SocAlert::getRuleDescription, batchId))
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT 100"));
        Map<Long, SocAlert> alertsById = alerts.stream()
                .filter(alert -> alert.getId() != null)
                .collect(java.util.stream.Collectors.toMap(SocAlert::getId, alert -> alert, (left, ignored) -> left, LinkedHashMap::new));
        for (SocExternalEvent event : events) {
            if (event.getAlertId() != null && !alertsById.containsKey(event.getAlertId())) {
                SocAlert alert = alertMapper.selectById(event.getAlertId());
                if (alert != null && securityScope.canAccess(alert.getOwnerId(), alert.getDeptId())) {
                    alertsById.put(alert.getId(), alert);
                }
            }
        }
        List<SocAlert> linkedAlerts = new ArrayList<>(alertsById.values());
        linkedAlerts.forEach(this::enrichAlertEvidence);
        List<Long> ticketIds = linkedAlerts.stream()
                .map(SocAlert::getTicketId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<SocTicket> tickets = ticketIds.isEmpty()
                ? List.of()
                : ticketMapper.selectList(scopedTicketWrapper()
                .in(SocTicket::getId, ticketIds)
                .orderByDesc(SocTicket::getCreatedAt));
        List<SocVulnerability> vulnerabilities = vulnerabilityMapper.selectList(scopedVulnerabilityWrapper()
                .and(w -> w.like(SocVulnerability::getAssetName, batchId)
                        .or().like(SocVulnerability::getFixSuggestion, batchId)
                        .or().like(SocVulnerability::getCveId, "DEMO-RANGE"))
                .orderByDesc(SocVulnerability::getDetectedAt)
                .last("LIMIT 100"));
        List<SocReport> reports = reportMapper.selectList(new LambdaQueryWrapper<SocReport>()
                .and(w -> w.like(SocReport::getTitle, batchId)
                        .or().like(SocReport::getSummary, batchId)
                        .or().like(SocReport::getReportNo, batchId))
                .orderByDesc(SocReport::getGeneratedAt)
                .last("LIMIT 20"));
        List<SocNotificationLog> notificationLogs = notificationService == null ? List.of() : notificationService.logsByKeyword(batchId, 20);
        long blocked = events.stream()
                .filter(event -> {
                    DemoEvidenceFields fields = evidenceFieldsFromJson(firstNotBlank(event.getNormalizedEvent(), event.getRawEvent()));
                    return "block".equalsIgnoreCase(fields.action())
                            || Objects.toString(event.getEventType(), "").toLowerCase(Locale.ROOT).endsWith("_block");
                })
                .count();
        String sourceCoverage = events.stream()
                .map(SocExternalEvent::getSourceType)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
        DemoRangeChainSummary summary = new DemoRangeChainSummary(batchId, events.size(), linkedAlerts.size(),
                vulnerabilities.size(), blocked, tickets.size(), reports.size(), notificationLogs.size(), sourceCoverage);
        return new DemoRangeEvidenceChain(summary, events.stream().limit(20).toList(), linkedAlerts.stream().limit(20).toList(),
                vulnerabilities.stream().limit(20).toList(), tickets.stream().limit(20).toList(), reports,
                notificationLogs);
    }

    public CyberChefAnalysis analyzeWithCyberChef(CyberChefAnalysisRequest request) {
        String value = request.value().trim();
        List<String> operations = new ArrayList<>();
        Map<String, String> findings = new LinkedHashMap<>();
        findings.put("length", String.valueOf(value.length()));
        findings.put("sha256", hash(value + "|cyberchef-analysis"));
        if (value.matches("(?i)^[A-Z0-9+/=]{12,}$")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(value);
                findings.put("base64DecodedPreview", limit(new String(decoded, StandardCharsets.UTF_8), 240));
                operations.add("From Base64");
            } catch (IllegalArgumentException ignored) {
                findings.put("base64", "not-decodable");
            }
        }
        if (value.contains("%")) {
            findings.put("urlDecoded", limit(URLDecoder.decode(value, StandardCharsets.UTF_8), 240));
            operations.add("URL Decode");
        }
        if (value.matches(".*\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b.*")) {
            findings.put("ipCandidate", "true");
            operations.add("Extract IP addresses");
        }
        if (value.matches("(?i).*https?://[^\\s]+.*")) {
            findings.put("urlCandidate", "true");
            operations.add("Extract URLs");
        }
        if (operations.isEmpty()) {
            operations.add("Fork / Magic");
        }
        return new CyberChefAnalysis(firstNotBlank(request.fieldName(), "selected_field"), operations, findings,
                "本地演示分析只做安全字段解码、摘要和 IOC 候选识别；真实 CyberChef 页面由 13-CyberChef 作为外部分析入口承载。");
    }

    @Transactional
    public AutomationDemoResult sendShuffleDemoNotification() {
        dispatchNotification("automation_demo", "medium", "shuffle", 16L,
                "Shuffle demo workflow dry-run",
                "CyberFusion SOC 已触发演示自动化通知：告警归一化、转工单和报表闭环可由 16-Shuffle adapter 接入。");
        return new AutomationDemoResult("shuffle", "DRY_RUN", "已写入通知日志，未发送真实 Webhook、邮件或外部请求。");
    }

    public PageResult<SocAlertWhitelist> alertWhitelists(SocPageRequest request) {
        LambdaQueryWrapper<SocAlertWhitelist> wrapper = new LambdaQueryWrapper<SocAlertWhitelist>()
                .and(notBlank(request.keyword()), w -> w.like(SocAlertWhitelist::getRuleName, request.keyword())
                        .or().like(SocAlertWhitelist::getRuleId, request.keyword())
                        .or().like(SocAlertWhitelist::getAssetIp, request.keyword())
                        .or().like(SocAlertWhitelist::getSourceIp, request.keyword()))
                .eq("enabled".equals(request.status()), SocAlertWhitelist::getEnabled, 1)
                .eq("disabled".equals(request.status()), SocAlertWhitelist::getEnabled, 0)
                .orderByDesc(SocAlertWhitelist::getEnabled)
                .orderByDesc(SocAlertWhitelist::getUpdatedAt);
        securityScope.applyDataScope(wrapper, SocAlertWhitelist::getOwnerId, SocAlertWhitelist::getDeptId);
        return PageResult.from(whitelistMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    @Transactional
    public SocAlertWhitelist createAlertWhitelist(AlertWhitelistRequest request) {
        validateWhitelistRequest(request);
        SocAlertWhitelist item = new SocAlertWhitelist();
        applyWhitelistRequest(item, request);
        item.setOwnerId(securityScope.currentUserId());
        item.setDeptId(securityScope.currentDeptId());
        item.setMatchCount(0);
        whitelistMapper.insert(item);
        return item;
    }

    @Transactional
    public SocAlertWhitelist updateAlertWhitelist(Long id, AlertWhitelistRequest request) {
        validateWhitelistRequest(request);
        SocAlertWhitelist item = whitelistMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("白名单规则不存在");
        }
        ensureAccess(item.getOwnerId(), item.getDeptId(), "无权修改该白名单规则");
        applyWhitelistRequest(item, request);
        whitelistMapper.updateById(item);
        return item;
    }

    @Transactional
    public SocAlertWhitelist updateAlertWhitelistStatus(Long id, SocStatusRequest request) {
        SocAlertWhitelist item = whitelistMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("白名单规则不存在");
        }
        ensureAccess(item.getOwnerId(), item.getDeptId(), "无权修改该白名单规则");
        validateStatus(request.targetStatus(), "enabled", "disabled");
        item.setEnabled("enabled".equals(request.targetStatus()) ? 1 : 0);
        whitelistMapper.updateById(item);
        return item;
    }

    public AlertNoiseSummary alertNoiseSummary() {
        List<SocAlert> alerts = recentScopedAlerts(500);
        long whitelistHits = alerts.stream().filter(alert -> matchWhitelist(alert).isPresent()).count();
        long duplicateGroups = duplicateGroups(alerts).stream().filter(item -> item.repeatCount() > 1).count();
        long falsePositiveAlerts = alertMapper.selectCount(scopedAlertWrapper().eq(SocAlert::getStatus, "false_positive"));
        long activeWhitelists = whitelistMapper.selectCount(scopedWhitelistWrapper()
                .eq(SocAlertWhitelist::getEnabled, 1)
                .and(w -> w.isNull(SocAlertWhitelist::getExpiresAt).or().ge(SocAlertWhitelist::getExpiresAt, LocalDateTime.now())));
        return new AlertNoiseSummary(activeWhitelists, whitelistHits, falsePositiveAlerts, duplicateGroups);
    }

    public List<AlertAggregation> alertAggregations(SocPageRequest request) {
        List<SocAlert> alerts = recentScopedAlerts(Math.min((int) Math.max(request.pageSize(), 10), 500));
        return duplicateGroups(alerts).stream()
                .filter(item -> item.repeatCount() > 1)
                .limit(request.pageSize())
                .toList();
    }

    private NormalizedSuricataEvent normalizeSuricata(JsonNode raw) throws JsonProcessingException {
        String eventType = text(raw, "event_type", "unknown");
        JsonNode alert = raw.path("alert");
        boolean alertEvent = raw.has("alert") && !alert.isMissingNode();
        String srcIp = text(raw, "src_ip", null);
        String destIp = text(raw, "dest_ip", null);
        String assetIp = firstNotBlank(destIp, text(raw, "dest_ip", null));
        SocAsset asset = assetIp == null ? null : assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
        String ruleId = alertEvent ? text(alert, "signature_id", null) : null;
        String ruleName = alertEvent ? text(alert, "signature", eventType) : eventType;
        String severity = alertEvent ? mapSuricataSeverity(alert.path("severity").asInt(3), ruleName) : "low";
        LocalDateTime eventTime = parseSuricataTime(text(raw, "timestamp", null));
        String eventUid = "SURICATA-" + hash(String.join("|",
                text(raw, "timestamp", ""),
                text(raw, "flow_id", ""),
                Objects.toString(srcIp, ""),
                Objects.toString(destIp, ""),
                Objects.toString(ruleId, ""),
                ruleName));
        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        normalizedPayload.put("source", "suricata");
        normalizedPayload.put("event_type", normalizeSuricataEventType(eventType, alertEvent));
        normalizedPayload.put("severity", severity);
        normalizedPayload.put("asset", asset == null ? Objects.toString(assetIp, "") : asset.getHostname());
        normalizedPayload.put("ioc", Objects.toString(srcIp, ""));
        normalizedPayload.put("rule", ruleName);
        copyDemoMetadata(raw, normalizedPayload);
        String normalizedEvent = objectMapper.writeValueAsString(normalizedPayload);
        Long ownerId = asset == null || asset.getOwnerId() == null ? securityScope.currentUserId() : asset.getOwnerId();
        Long deptId = asset == null || asset.getDeptId() == null ? securityScope.currentDeptId() : asset.getDeptId();
        return new NormalizedSuricataEvent(eventUid, normalizeSuricataEventType(eventType, alertEvent), severity,
                ruleId, ruleName, srcIp, destIp, asset == null ? assetIp : asset.getHostname(), assetIp,
                srcIp, objectMapper.writeValueAsString(raw), normalizedEvent, ownerId, deptId, eventTime, alertEvent);
    }

    private UpsertResult upsertExternalEvent(NormalizedSuricataEvent normalized, Long alertId) {
        SocExternalEvent item = externalEventMapper.selectOne(new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getEventUid, normalized.eventUid())
                .last("LIMIT 1"));
        boolean created = item == null;
        if (item == null) {
            item = new SocExternalEvent();
            item.setEventUid(normalized.eventUid());
            item.setStatus(alertId == null ? "new" : "linked");
        }
        item.setSourceType("suricata");
        item.setEventType(normalized.eventType());
        item.setSeverity(normalized.severity());
        item.setRuleId(normalized.ruleId());
        item.setRuleName(normalized.ruleName());
        item.setSrcIp(normalized.srcIp());
        item.setDestIp(normalized.destIp());
        item.setAssetName(normalized.assetName());
        item.setAssetIp(normalized.assetIp());
        DemoEvidenceFields evidence = evidenceFieldsFromJson(firstNotBlank(normalized.normalizedEvent(), normalized.rawEvent()));
        item.setBatchId(evidence.batchId());
        item.setDemoCaseId(evidence.demoCaseId());
        item.setTargetUrl(evidence.targetUrl());
        item.setAction(evidence.action());
        item.setRequestId(evidence.requestId());
        item.setCorrelationKey(correlationFingerprint("suricata", normalized.eventType(), normalized.ruleId(),
                normalized.assetIp(), evidence.targetUrl(), evidence.batchId(), evidence.demoCaseId(), normalized.eventTime()));
        item.setIoc(normalized.ioc());
        item.setRawEvent(normalized.rawEvent());
        item.setNormalizedEvent(normalized.normalizedEvent());
        item.setAlertId(alertId);
        item.setOwnerId(normalized.ownerId());
        item.setDeptId(normalized.deptId());
        item.setEventTime(normalized.eventTime());
        if (created) {
            externalEventMapper.insert(item);
        } else {
            externalEventMapper.updateById(item);
        }
        return new UpsertResult(created);
    }

    private SocAlert upsertSuricataAlert(NormalizedSuricataEvent normalized) {
        String alertUid = "SURICATA-IMPORT-" + normalized.eventUid().replace("SURICATA-", "");
        SocAlert alert = alertMapper.selectOne(new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getAlertUid, alertUid)
                .last("LIMIT 1"));
        boolean created = alert == null;
        if (alert == null) {
            alert = new SocAlert();
            alert.setAlertUid(alertUid);
            alert.setStatus("new");
        }
        alert.setSourceType("suricata");
        alert.setLevel(levelOf(normalized.severity()));
        alert.setSeverity(normalized.severity());
        alert.setRuleId(firstNotBlank(normalized.ruleId(), "SURICATA"));
        alert.setRuleDescription(normalized.ruleName());
        alert.setAssetName(firstNotBlank(normalized.assetName(), normalized.assetIp(), "unknown-asset"));
        alert.setAssetIp(firstNotBlank(normalized.assetIp(), normalized.destIp(), "0.0.0.0"));
        alert.setSourceIp(normalized.srcIp());
        alert.setTactic(tacticOf(normalized.eventType()));
        alert.setRawRef(importRawRef("suricata/eve.json", normalized.eventUid(), normalized.normalizedEvent()));
        alert.setEventTime(normalized.eventTime());
        alert.setOwnerId(normalized.ownerId());
        alert.setDeptId(normalized.deptId());
        if (created) {
            alertMapper.insert(alert);
        } else {
            alertMapper.updateById(alert);
        }
        return alert;
    }

    private List<String> jsonRecords(String content, String sourceType) throws BusinessException {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<String> records = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(item -> records.add(item.toString()));
                return records;
            }
            if ("misp".equals(sourceType)) {
                JsonNode attributes = root.path("Attribute").isArray() ? root.path("Attribute") : root.path("attributes");
                if (attributes.isArray()) {
                    attributes.forEach(item -> records.add(item.toString()));
                    return records;
                }
            }
            if ("zap".equals(sourceType) && root.path("site").isArray()) {
                root.path("site").forEach(site -> site.path("alerts").forEach(alert -> records.add(alert.toString())));
                return records;
            }
            records.add(root.toString());
            return records;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("导入内容不是合法 JSON 或 JSON Lines");
        }
    }

    private List<NormalizedCyberFusionEvent> normalizeCyberFusionLine(String sourceType, String line, String[] zeekFields) throws JsonProcessingException {
        if ("zeek".equals(sourceType) && !line.startsWith("{")) {
            return normalizeZeek(line, zeekFields);
        }
        JsonNode raw = objectMapper.readTree(line);
        Optional<NormalizedCyberFusionEvent> configured = normalizeConfiguredCyberFusionEvent(sourceType, raw);
        if (configured.isPresent()) {
            return List.of(configured.get());
        }
        return switch (sourceType) {
            case "wazuh" -> List.of(normalizeWazuh(raw));
            case "misp" -> List.of(normalizeMisp(raw));
            case "zap" -> List.of(normalizeZap(raw));
            case "suricata" -> List.of(normalizeSuricataCyberFusion(raw));
            case "zeek" -> List.of(normalizeZeekJson(raw));
            case "waf" -> List.of(normalizeWaf(raw));
            case "sigma" -> List.of(normalizeRuleRecord(sourceType, raw));
            case "shuffle" -> List.of(normalizeAutomationRecord(sourceType, raw));
            case "falco", "opencti", "osquery", "velociraptor", "cowrie" -> List.of(normalizeGenericJson(sourceType, raw));
            default -> List.of();
        };
    }

    private boolean hasConfiguredEventAdapter(String sourceType) {
        try {
            return eventAdapterPolicyService != null && eventAdapterPolicyService.hasActive(sourceType);
        } catch (Exception ex) {
            return false;
        }
    }

    private Optional<NormalizedCyberFusionEvent> normalizeConfiguredCyberFusionEvent(String sourceType, JsonNode raw) throws JsonProcessingException {
        if (eventAdapterPolicyService == null) {
            return Optional.empty();
        }
        try {
            Optional<EventAdapterPolicyService.AdapterPreviewResult> preview = eventAdapterPolicyService.previewActive(sourceType, raw);
            if (preview.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(configuredPreviewToEvent(sourceType, raw, preview.get()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private NormalizedCyberFusionEvent configuredPreviewToEvent(String sourceType, JsonNode raw,
                                                               EventAdapterPolicyService.AdapterPreviewResult preview) throws JsonProcessingException {
        Map<String, Object> normalized = new LinkedHashMap<>(preview.normalizedEvent());
        normalized.putIfAbsent("source", sourceType);
        String eventType = firstNotBlank(value(normalized, "eventType"), value(normalized, "event_type"), sourceType + "_event");
        String severity = normalizeSeverity(firstNotBlank(preview.severity(), value(normalized, "severity"), "medium"));
        String ruleId = firstNotBlank(value(normalized, "ruleId"), value(normalized, "rule_id"), value(normalized, "pluginId"), sourceType.toUpperCase(Locale.ROOT));
        String ruleName = firstNotBlank(value(normalized, "ruleName"), value(normalized, "rule_name"), value(normalized, "name"), value(normalized, "rule"), sourceType + " event");
        String targetUrl = firstNotBlank(value(normalized, "targetUrl"), value(normalized, "target_url"), value(normalized, "url"));
        String srcIp = safeIpCandidate(firstNotBlank(value(normalized, "srcIp"), value(normalized, "src_ip"), value(normalized, "sourceIp"), value(normalized, "source_ip"), text(raw, "src_ip", null)));
        String destIp = safeIpCandidate(firstNotBlank(value(normalized, "destIp"), value(normalized, "dest_ip"), value(normalized, "assetIp"), value(normalized, "asset_ip"), text(raw, "dest_ip", null)));
        String assetIp = safeIpCandidate(firstNotBlank(value(normalized, "assetIp"), value(normalized, "asset_ip"), destIp));
        String assetNameHint = firstNotBlank(value(normalized, "assetName"), value(normalized, "asset_name"), value(normalized, "targetHost"));
        String ioc = firstNotBlank(value(normalized, "ioc"), targetUrl, srcIp, assetIp);
        String dedup = firstNotBlank(preview.dedupKey(), value(normalized, "requestId"), value(normalized, "uid"), value(normalized, "id"), raw.toString());
        String eventUid = sourceType.toUpperCase(Locale.ROOT) + "-CFG-" + hash(dedup + "|" + eventType);
        SocAsset asset = assetIp == null ? null : assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
        Long ownerId = asset == null || asset.getOwnerId() == null ? securityScope.currentUserId() : asset.getOwnerId();
        Long deptId = asset == null || asset.getDeptId() == null ? securityScope.currentDeptId() : asset.getDeptId();
        return new NormalizedCyberFusionEvent(sourceType, eventUid, eventType, severity, ruleId, ruleName,
                srcIp, destIp, asset == null ? firstNotBlank(assetNameHint, assetIp, sourceType + "-asset") : asset.getHostname(),
                assetIp, ioc, objectMapper.writeValueAsString(raw), objectMapper.writeValueAsString(normalized),
                ownerId, deptId, parseAnyTime(firstNotBlank(value(normalized, "timestamp"), value(normalized, "time"), text(raw, "timestamp", null), text(raw, "@timestamp", null))),
                preview.willCreateAlert());
    }

    private NormalizedCyberFusionEvent normalizeSuricataCyberFusion(JsonNode raw) throws JsonProcessingException {
        NormalizedSuricataEvent event = normalizeSuricata(raw);
        return new NormalizedCyberFusionEvent("suricata", event.eventUid(), event.eventType(), event.severity(),
                event.ruleId(), event.ruleName(), event.srcIp(), event.destIp(), event.assetName(), event.assetIp(),
                event.ioc(), event.rawEvent(), event.normalizedEvent(), event.ownerId(), event.deptId(), event.eventTime(), event.alertEvent());
    }

    private List<NormalizedCyberFusionEvent> normalizeZeek(String line, String[] zeekFields) throws JsonProcessingException {
        String[] values = line.split("\\t", -1);
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            String field = i < zeekFields.length ? zeekFields[i] : "field_" + i;
            row.put(field, "-".equals(values[i]) ? "" : values[i]);
        }
        String srcIp = firstNotBlank(row.get("id.orig_h"), row.get("uid"));
        String destIp = row.get("id.resp_h");
        String ruleName = "Zeek " + firstNotBlank(row.get("service"), row.get("proto"), "connection") + " log";
        String eventUid = "ZEEK-" + hash(line);
        return List.of(normalizedEvent("zeek", eventUid, "network_connection", "medium", row.get("uid"), ruleName,
                srcIp, destIp, destIp, srcIp, objectMapper.writeValueAsString(row), true, parseEpochOrNow(row.get("ts"))));
    }

    private NormalizedCyberFusionEvent normalizeZeekJson(JsonNode raw) throws JsonProcessingException {
        String srcIp = firstNotBlank(text(raw, "id.orig_h", null), text(raw, "src_ip", null));
        String destIp = firstNotBlank(text(raw, "id.resp_h", null), text(raw, "dest_ip", null));
        String ruleName = "Zeek " + firstNotBlank(text(raw, "service", null), text(raw, "proto", null), "connection") + " log";
        String eventUid = "ZEEK-" + hash(raw.toString());
        return normalizedEvent("zeek", eventUid, "network_connection", "medium", text(raw, "uid", null), ruleName,
                srcIp, destIp, destIp, srcIp, objectMapper.writeValueAsString(raw), true, parseEpochOrNow(text(raw, "ts", null)));
    }

    private NormalizedCyberFusionEvent normalizeWazuh(JsonNode raw) throws JsonProcessingException {
        JsonNode rule = raw.path("rule");
        int level = rule.path("level").asInt(3);
        String severity = level >= 12 ? "critical" : level >= 8 ? "high" : level >= 4 ? "medium" : "low";
        String assetIp = firstNotBlank(text(raw.path("agent"), "ip", null), findText(raw, "data.dstip", "data.dest_ip"));
        String srcIp = findText(raw, "data.srcip", "data.src_ip", "src_ip");
        String ruleId = text(rule, "id", "WAZUH");
        String ruleName = text(rule, "description", "Wazuh alert");
        String eventUid = "WAZUH-" + hash(firstNotBlank(text(raw, "id", null), raw.toString()));
        return normalizedEvent("wazuh", eventUid, "xdr_alert", severity, ruleId, ruleName,
                srcIp, assetIp, assetIp, firstNotBlank(srcIp, assetIp), objectMapper.writeValueAsString(raw), true,
                parseAnyTime(firstNotBlank(text(raw, "timestamp", null), text(raw, "@timestamp", null))));
    }

    private NormalizedCyberFusionEvent normalizeMisp(JsonNode raw) throws JsonProcessingException {
        String value = firstNotBlank(text(raw, "value", null), findText(raw, "Attribute.value"));
        String type = firstNotBlank(text(raw, "type", null), findText(raw, "Attribute.type"), "ioc");
        String category = firstNotBlank(text(raw, "category", null), "misp_attribute");
        String eventUid = "MISP-" + hash(firstNotBlank(text(raw, "uuid", null), value, raw.toString()));
        String severity = "ip-dst".equals(type) || "url".equals(type) || "domain".equals(type) ? "high" : "medium";
        return normalizedEvent("misp", eventUid, "threat_intel_ioc", severity, type, "MISP IOC " + category + " / " + type,
                null, null, null, value, objectMapper.writeValueAsString(raw), true, parseAnyTime(firstNotBlank(text(raw, "timestamp", null), text(raw, "date", null))));
    }

    private NormalizedCyberFusionEvent normalizeZap(JsonNode raw) throws JsonProcessingException {
        String risk = firstNotBlank(text(raw, "riskcode", null), text(raw, "riskdesc", null), text(raw, "risk", null), "low");
        String severity = risk.toLowerCase(Locale.ROOT).contains("high") || "3".equals(risk) ? "high"
                : risk.toLowerCase(Locale.ROOT).contains("medium") || "2".equals(risk) ? "medium" : "low";
        String pluginId = firstNotBlank(text(raw, "pluginid", null), text(raw, "pluginId", null), "ZAP");
        String name = firstNotBlank(text(raw, "name", null), text(raw, "alert", null), "ZAP finding");
        String eventUid = "ZAP-" + hash(pluginId + "|" + name + "|" + text(raw, "url", ""));
        String targetUrl = firstNotBlank(text(raw, "url", null), text(raw, "targetUrl", null), "web-target");
        return normalizedEvent("zap", eventUid, "web_app_finding", severity, pluginId, name,
                null, null, null, targetUrl,
                objectMapper.writeValueAsString(raw), true, LocalDateTime.now());
    }

    private NormalizedCyberFusionEvent normalizeWaf(JsonNode raw) throws JsonProcessingException {
        String rawEventType = firstNotBlank(text(raw, "eventType", null), text(raw, "event_type", null), "waf_detect");
        String eventType = normalizeWafEventType(rawEventType, text(raw, "action", null));
        String action = firstNotBlank(text(raw, "action", null), eventType.endsWith("_block") ? "block" : "detect");
        String severity = normalizeSeverity(firstNotBlank(text(raw, "severity", null), eventType.endsWith("_block") ? "high" : "medium"));
        String assetIp = firstNotBlank(text(raw, "assetIp", null), text(raw, "asset_ip", null), text(raw, "dest_ip", null), text(raw, "dst_ip", null));
        String srcIp = firstNotBlank(text(raw, "srcIp", null), text(raw, "sourceIp", null), text(raw, "clientIp", null), text(raw, "src_ip", null), text(raw, "source_ip", null));
        String targetUrl = firstNotBlank(text(raw, "targetUrl", null), text(raw, "url", null), text(raw, "request_uri", null), "local-waf-demo");
        String method = firstNotBlank(text(raw, "httpMethod", null), text(raw, "method", null), "GET");
        String httpStatus = firstNotBlank(text(raw, "httpStatus", null), text(raw, "status", null), eventType.endsWith("_block") ? "403" : "200");
        String engine = firstNotBlank(text(raw, "engine", null), "WAF demo gateway");
        String requestId = firstNotBlank(text(raw, "requestId", null), text(raw, "request_id", null), text(raw, "id", null), "waf-" + hash(raw.toString()));
        String demoCaseId = firstNotBlank(text(raw, "demoCaseId", null), text(raw, "demo_case_id", null), "demo-range-waf");
        String batchId = firstNotBlank(text(raw, "batchId", null), text(raw, "demoBatchId", null), text(raw, "batch_id", null), DEFAULT_DEMO_RANGE_BATCH_ID);
        String ruleId = firstNotBlank(text(raw, "ruleId", null), text(raw, "rule_id", null), "WAF-DEMO");
        String ruleName = firstNotBlank(text(raw, "ruleName", null), text(raw, "rule_name", null), text(raw, "message", null), wafRuleName(eventType, action));
        String evidenceSummary = firstNotBlank(text(raw, "evidenceSummary", null), text(raw, "summary", null),
                "%s %s %s -> HTTP %s by %s".formatted(action, method, targetUrl, httpStatus, engine));
        String rawEvent = objectMapper.writeValueAsString(raw);
        String eventUid = "WAF-" + hash(requestId + "|" + demoCaseId + "|" + eventType);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("source", "waf");
        normalized.put("event_type", eventType);
        normalized.put("severity", severity);
        normalized.put("asset", Objects.toString(assetIp, ""));
        normalized.put("rule", ruleName);
        normalized.put("rule_id", ruleId);
        normalized.put("target_url", targetUrl);
        normalized.put("http_method", method);
        normalized.put("http_status", httpStatus);
        normalized.put("action", action);
        normalized.put("engine", engine);
        normalized.put("request_id", requestId);
        normalized.put("demo_case_id", demoCaseId);
        normalized.put("demoCaseId", demoCaseId);
        normalized.put("batch_id", batchId);
        normalized.put("batchId", batchId);
        normalized.put("evidence_summary", evidenceSummary);
        normalized.put("evidenceSummary", evidenceSummary);
        SocAsset asset = assetIp == null ? null : assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
        Long ownerId = asset == null || asset.getOwnerId() == null ? securityScope.currentUserId() : asset.getOwnerId();
        Long deptId = asset == null || asset.getDeptId() == null ? securityScope.currentDeptId() : asset.getDeptId();
        return new NormalizedCyberFusionEvent("waf", eventUid, eventType, severity, ruleId, ruleName,
                srcIp, assetIp, asset == null ? firstNotBlank(assetIp, "waf-protected-service") : asset.getHostname(),
                assetIp, targetUrl, rawEvent, objectMapper.writeValueAsString(normalized), ownerId, deptId,
                parseAnyTime(firstNotBlank(text(raw, "timestamp", null), text(raw, "time", null))), true);
    }

    private NormalizedCyberFusionEvent normalizeRuleRecord(String sourceType, JsonNode raw) throws JsonProcessingException {
        String title = firstNotBlank(text(raw, "title", null), text(raw, "name", null), sourceType + " rule");
        String eventUid = sourceType.toUpperCase(Locale.ROOT) + "-" + hash(firstNotBlank(text(raw, "id", null), title));
        return normalizedEvent(sourceType, eventUid, "detection_rule", "low", text(raw, "id", sourceType.toUpperCase(Locale.ROOT)), title,
                null, null, null, firstNotBlank(text(raw, "tags", null), text(raw, "logsource", null)),
                objectMapper.writeValueAsString(raw), false, LocalDateTime.now());
    }

    private NormalizedCyberFusionEvent normalizeAutomationRecord(String sourceType, JsonNode raw) throws JsonProcessingException {
        String name = firstNotBlank(text(raw, "name", null), text(raw, "workflow", null), "Shuffle demo workflow");
        String eventUid = sourceType.toUpperCase(Locale.ROOT) + "-" + hash(name + raw.toString());
        return normalizedEvent(sourceType, eventUid, "automation_workflow", "medium", text(raw, "id", "SHUFFLE"), name,
                null, null, "SOAR", name, objectMapper.writeValueAsString(raw), false, LocalDateTime.now());
    }

    private NormalizedCyberFusionEvent normalizeGenericJson(String sourceType, JsonNode raw) throws JsonProcessingException {
        String srcIp = firstNotBlank(text(raw, "src_ip", null), text(raw, "source_ip", null), findText(raw, "data.src_ip"));
        String destIp = firstNotBlank(text(raw, "dest_ip", null), text(raw, "dst_ip", null), findText(raw, "data.dest_ip"));
        String name = firstNotBlank(text(raw, "rule", null), text(raw, "name", null), text(raw, "message", null), sourceType + " event");
        String severity = normalizeSeverity(firstNotBlank(text(raw, "severity", null), text(raw, "level", null), "medium"));
        String eventUid = sourceType.toUpperCase(Locale.ROOT) + "-" + hash(firstNotBlank(text(raw, "id", null), raw.toString()));
        return normalizedEvent(sourceType, eventUid, "external_signal", severity, text(raw, "rule_id", sourceType.toUpperCase(Locale.ROOT)), name,
                srcIp, destIp, destIp, firstNotBlank(srcIp, destIp), objectMapper.writeValueAsString(raw), true,
                parseAnyTime(firstNotBlank(text(raw, "timestamp", null), text(raw, "time", null))));
    }

    private NormalizedCyberFusionEvent normalizedEvent(String sourceType, String eventUid, String eventType, String severity,
                                                       String ruleId, String ruleName, String srcIp, String destIp,
                                                       String assetIp, String ioc, String rawEvent, boolean alertEvent,
                                                       LocalDateTime eventTime) throws JsonProcessingException {
        SocAsset asset = assetIp == null ? null : assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        normalizedPayload.put("source", sourceType);
        normalizedPayload.put("event_type", eventType);
        normalizedPayload.put("severity", severity);
        normalizedPayload.put("asset", asset == null ? Objects.toString(assetIp, "") : asset.getHostname());
        normalizedPayload.put("ioc", Objects.toString(ioc, ""));
        normalizedPayload.put("rule", Objects.toString(ruleName, ""));
        copyDemoMetadata(rawEvent, normalizedPayload);
        String normalizedEvent = objectMapper.writeValueAsString(normalizedPayload);
        Long ownerId = asset == null || asset.getOwnerId() == null ? securityScope.currentUserId() : asset.getOwnerId();
        Long deptId = asset == null || asset.getDeptId() == null ? securityScope.currentDeptId() : asset.getDeptId();
        return new NormalizedCyberFusionEvent(sourceType, eventUid, eventType, severity, ruleId, ruleName,
                srcIp, destIp, asset == null ? firstNotBlank(assetIp, destIp, "external") : asset.getHostname(),
                assetIp, ioc, rawEvent, normalizedEvent, ownerId, deptId, eventTime, alertEvent);
    }

    private UpsertResult upsertExternalEvent(NormalizedCyberFusionEvent normalized, Long alertId) {
        SocExternalEvent item = externalEventMapper.selectOne(new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getEventUid, normalized.eventUid())
                .last("LIMIT 1"));
        boolean created = item == null;
        if (item == null) {
            item = new SocExternalEvent();
            item.setEventUid(normalized.eventUid());
            item.setStatus(alertId == null ? "new" : "linked");
        }
        item.setSourceType(normalized.sourceType());
        item.setEventType(normalized.eventType());
        item.setSeverity(normalized.severity());
        item.setRuleId(normalized.ruleId());
        item.setRuleName(normalized.ruleName());
        item.setSrcIp(normalized.srcIp());
        item.setDestIp(normalized.destIp());
        item.setAssetName(normalized.assetName());
        item.setAssetIp(normalized.assetIp());
        DemoEvidenceFields evidence = evidenceFieldsFromJson(firstNotBlank(normalized.normalizedEvent(), normalized.rawEvent()));
        item.setBatchId(evidence.batchId());
        item.setDemoCaseId(evidence.demoCaseId());
        item.setTargetUrl(evidence.targetUrl());
        item.setAction(evidence.action());
        item.setRequestId(evidence.requestId());
        item.setCorrelationKey(correlationFingerprint(normalized.sourceType(), normalized.eventType(), normalized.ruleId(),
                firstNotBlank(normalized.assetIp(), normalized.destIp()), evidence.targetUrl(), evidence.batchId(), evidence.demoCaseId(), normalized.eventTime()));
        item.setIoc(normalized.ioc());
        item.setRawEvent(normalized.rawEvent());
        item.setNormalizedEvent(normalized.normalizedEvent());
        item.setAlertId(alertId);
        item.setOwnerId(normalized.ownerId());
        item.setDeptId(normalized.deptId());
        item.setEventTime(normalized.eventTime());
        if (created) {
            externalEventMapper.insert(item);
        } else {
            externalEventMapper.updateById(item);
        }
        return new UpsertResult(created);
    }

    private SocAlert upsertCyberFusionAlert(NormalizedCyberFusionEvent normalized) {
        String alertUid = normalized.sourceType().toUpperCase(Locale.ROOT) + "-IMPORT-" + normalized.eventUid().replace(normalized.sourceType().toUpperCase(Locale.ROOT) + "-", "");
        SocAlert alert = alertMapper.selectOne(new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getAlertUid, alertUid)
                .last("LIMIT 1"));
        boolean created = alert == null;
        if (alert == null) {
            alert = new SocAlert();
            alert.setAlertUid(alertUid);
            alert.setStatus("new");
        }
        alert.setSourceType(normalized.sourceType());
        alert.setLevel(levelOf(normalized.severity()));
        alert.setSeverity(normalized.severity());
        alert.setRuleId(firstNotBlank(normalized.ruleId(), normalized.sourceType().toUpperCase(Locale.ROOT)));
        alert.setRuleDescription(normalized.ruleName());
        alert.setAssetName(firstNotBlank(normalized.assetName(), normalized.assetIp(), "external-asset"));
        alert.setAssetIp(firstNotBlank(normalized.assetIp(), normalized.destIp(), "0.0.0.0"));
        alert.setSourceIp(normalized.srcIp());
        DemoEvidenceFields evidence = evidenceFieldsFromJson(firstNotBlank(normalized.normalizedEvent(), normalized.rawEvent()));
        alert.setEventType(firstNotBlank(evidence.eventType(), normalized.eventType()));
        alert.setTargetUrl(evidence.targetUrl());
        alert.setAction(evidence.action());
        alert.setEvidenceSummary(evidence.evidenceSummary());
        alert.setBatchId(evidence.batchId());
        alert.setDemoCaseId(evidence.demoCaseId());
        alert.setCorrelationKey(correlationFingerprint(normalized.sourceType(), normalized.eventType(), normalized.ruleId(),
                firstNotBlank(normalized.assetIp(), normalized.destIp()), evidence.targetUrl(), evidence.batchId(), evidence.demoCaseId(), normalized.eventTime()));
        alert.setTactic(tacticOf(normalized.eventType()));
        alert.setRawRef(importRawRef(normalized.sourceType() + "/import", normalized.eventUid(), normalized.normalizedEvent()));
        alert.setEventTime(normalized.eventTime());
        alert.setOwnerId(normalized.ownerId());
        alert.setDeptId(normalized.deptId());
        if (created) {
            alertMapper.insert(alert);
        } else {
            alertMapper.updateById(alert);
        }
        return alert;
    }

    private String correlationFingerprint(String sourceType, String eventType, String ruleId, String assetIp,
                                          String targetUrl, String batchId, String demoCaseId, LocalDateTime eventTime) {
        String bucket = eventTime == null ? "unknown" : eventTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String material = String.join("|",
                firstNotBlank(assetIp, "asset-unknown"),
                firstNotBlank(sourceType, "source-unknown"),
                firstNotBlank(eventType, "event-unknown"),
                firstNotBlank(ruleId, "rule-unknown"),
                firstNotBlank(targetUrl, "url-unknown"),
                firstNotBlank(batchId, demoCaseId, "batch-unknown"),
                bucket.substring(0, Math.min(11, bucket.length())));
        return "corr-" + hash(material);
    }

    private CyberFusionImportResult importTrivyVulnerabilities(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<JsonNode> vulnerabilities = new ArrayList<>();
            if (root.path("Results").isArray()) {
                root.path("Results").forEach(result -> result.path("Vulnerabilities").forEach(vulnerabilities::add));
            } else if (root.path("Vulnerabilities").isArray()) {
                root.path("Vulnerabilities").forEach(vulnerabilities::add);
            } else if (root.isArray()) {
                root.forEach(vulnerabilities::add);
            }
            int imported = 0;
            for (JsonNode item : vulnerabilities) {
                upsertTrivyVulnerability(item);
                imported++;
            }
            return new CyberFusionImportResult("trivy", 0, 0, 0, 0, imported, 0, List.of());
        } catch (JsonProcessingException ex) {
            return new CyberFusionImportResult("trivy", 0, 0, 0, 0, 0, 1, List.of("JSON 格式错误"));
        }
    }

    private void upsertTrivyVulnerability(JsonNode raw) {
        String cveId = firstNotBlank(text(raw, "VulnerabilityID", null), text(raw, "id", null), "TRIVY-" + hash(raw.toString()));
        String assetName = firstNotBlank(text(raw, "Target", null), text(raw, "PkgName", null), "trivy-target");
        String packageName = firstNotBlank(text(raw, "PkgName", null), text(raw, "packageName", null), "unknown-package");
        SocVulnerability item = vulnerabilityMapper.selectOne(new LambdaQueryWrapper<SocVulnerability>()
                .eq(SocVulnerability::getCveId, cveId)
                .eq(SocVulnerability::getSoftwareName, packageName)
                .last("LIMIT 1"));
        if (item == null) {
            item = new SocVulnerability();
            item.setStatus("open");
        }
        item.setCveId(cveId);
        item.setSeverity(normalizeSeverity(firstNotBlank(text(raw, "Severity", null), "medium")));
        item.setAssetName(assetName);
        item.setAssetIp("0.0.0.0");
        item.setSoftwareName(packageName);
        item.setSoftwareVersion(firstNotBlank(text(raw, "InstalledVersion", null), text(raw, "version", null)));
        String batchId = firstNotBlank(text(raw, "batchId", null), text(raw, "demoBatchId", null));
        String fixSuggestion = firstNotBlank(text(raw, "FixedVersion", null), text(raw, "PrimaryURL", null), "按 Trivy 报告确认修复版本、补丁或风险接受依据");
        item.setFixSuggestion(notBlank(batchId) ? fixSuggestion + "；batchId=" + batchId : fixSuggestion);
        item.setSourceType("trivy");
        item.setOwnerId(securityScope.currentUserId());
        item.setDeptId(securityScope.currentDeptId());
        item.setDetectedAt(LocalDateTime.now());
        if (item.getId() == null) {
            vulnerabilityMapper.insert(item);
        } else {
            vulnerabilityMapper.updateById(item);
        }
    }

    private void addImportError(List<String> errors, int lineNumber, String message) {
        if (errors.size() < 5) {
            errors.add("line " + lineNumber + ": " + message);
        }
    }

    private String normalizeDemoBatchId(String batchId) {
        String normalized = trimToNull(batchId);
        return normalized == null ? DEFAULT_DEMO_RANGE_BATCH_ID : normalized;
    }

    private void copyDemoMetadata(String rawEvent, Map<String, Object> target) {
        if (!notBlank(rawEvent)) {
            return;
        }
        try {
            copyDemoMetadata(objectMapper.readTree(rawEvent), target);
        } catch (JsonProcessingException ignored) {
            // Non-JSON sources are valid for some adapters; batch metadata is optional.
        }
    }

    private void copyDemoMetadata(JsonNode raw, Map<String, Object> target) {
        DemoEvidenceFields fields = evidenceFieldsFromJson(raw.toString());
        if (notBlank(fields.sourceType())) {
            target.put("sourceType", fields.sourceType());
            target.put("source_type", fields.sourceType());
        }
        if (notBlank(fields.eventType())) {
            target.put("eventType", fields.eventType());
            target.put("event_type", fields.eventType());
        }
        if (notBlank(fields.ruleId())) {
            target.put("ruleId", fields.ruleId());
            target.put("rule_id", fields.ruleId());
        }
        if (notBlank(fields.ruleName())) {
            target.put("ruleName", fields.ruleName());
            target.put("rule_name", fields.ruleName());
        }
        if (notBlank(fields.assetIp())) {
            target.put("assetIp", fields.assetIp());
            target.put("asset_ip", fields.assetIp());
        }
        if (notBlank(fields.targetUrl())) {
            target.put("targetUrl", fields.targetUrl());
            target.put("target_url", fields.targetUrl());
        }
        if (notBlank(fields.action())) {
            target.put("action", fields.action());
        }
        if (notBlank(fields.evidenceSummary())) {
            target.put("evidenceSummary", fields.evidenceSummary());
            target.put("evidence_summary", fields.evidenceSummary());
        }
        if (notBlank(fields.demoCaseId())) {
            target.put("demoCaseId", fields.demoCaseId());
            target.put("demo_case_id", fields.demoCaseId());
        }
        if (notBlank(fields.batchId())) {
            target.put("batchId", fields.batchId());
            target.put("batch_id", fields.batchId());
        }
        if (notBlank(fields.httpMethod())) {
            target.put("httpMethod", fields.httpMethod());
            target.put("http_method", fields.httpMethod());
        }
        if (notBlank(fields.httpStatus())) {
            target.put("httpStatus", fields.httpStatus());
            target.put("http_status", fields.httpStatus());
        }
        if (notBlank(fields.requestId())) {
            target.put("requestId", fields.requestId());
            target.put("request_id", fields.requestId());
        }
        if (notBlank(fields.engine())) {
            target.put("engine", fields.engine());
        }
    }

    private String importRawRef(String prefix, String eventUid, String normalizedEvent) {
        String batchId = batchIdFromEventJson(normalizedEvent);
        return prefix + "/" + eventUid + (notBlank(batchId) ? "/batch/" + batchId : "");
    }

    private String batchIdFromEventJson(String eventJson) {
        if (!notBlank(eventJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(eventJson);
            return firstNotBlank(text(root, "batchId", null), text(root, "demoBatchId", null),
                    text(root, "batch_id", null), text(root, "demo_batch_id", null));
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void enrichAlertEvidence(SocAlert alert) {
        DemoEvidenceFields fields = alertEvidenceFields(alert);
        alert.setEventType(fields.eventType());
        alert.setRuleName(firstNotBlank(fields.ruleName(), alert.getRuleDescription()));
        alert.setTargetUrl(fields.targetUrl());
        alert.setAction(fields.action());
        alert.setEvidenceSummary(fields.evidenceSummary());
        alert.setDemoCaseId(fields.demoCaseId());
        alert.setBatchId(fields.batchId());
        alert.setHttpMethod(fields.httpMethod());
        alert.setHttpStatus(fields.httpStatus());
        alert.setRequestId(fields.requestId());
        alert.setEngine(fields.engine());
    }

    private DemoEvidenceFields alertEvidenceFields(SocAlert alert) {
        DemoEvidenceFields rawRefFields = evidenceFieldsFromRawRef(alert == null ? null : alert.getRawRef());
        SocExternalEvent event = linkedExternalEvent(alert);
        DemoEvidenceFields eventFields = event == null
                ? DemoEvidenceFields.empty()
                : evidenceFieldsFromJson(firstNotBlank(event.getNormalizedEvent(), event.getRawEvent()));
        return new DemoEvidenceFields(
                firstNotBlank(event == null ? null : event.getSourceType(), alert == null ? null : alert.getSourceType(), rawRefFields.sourceType()),
                firstNotBlank(event == null ? null : event.getEventType(), eventFields.eventType(), alert == null ? null : alert.getEventType(), rawRefFields.eventType()),
                firstNotBlank(event == null ? null : event.getRuleId(), eventFields.ruleId(), alert == null ? null : alert.getRuleId(), rawRefFields.ruleId()),
                firstNotBlank(event == null ? null : event.getRuleName(), eventFields.ruleName(), alert == null ? null : alert.getRuleDescription(), rawRefFields.ruleName()),
                firstNotBlank(event == null ? null : event.getAssetIp(), eventFields.assetIp(), alert == null ? null : alert.getAssetIp(), rawRefFields.assetIp()),
                firstNotBlank(event == null ? null : event.getTargetUrl(), eventFields.targetUrl(), alert == null ? null : alert.getTargetUrl(), rawRefFields.targetUrl()),
                firstNotBlank(event == null ? null : event.getAction(), eventFields.action(), alert == null ? null : alert.getAction(), rawRefFields.action()),
                firstNotBlank(eventFields.evidenceSummary(), alert == null ? null : alert.getEvidenceSummary(), rawRefFields.evidenceSummary()),
                firstNotBlank(event == null ? null : event.getDemoCaseId(), eventFields.demoCaseId(), alert == null ? null : alert.getDemoCaseId(), rawRefFields.demoCaseId()),
                firstNotBlank(event == null ? null : event.getBatchId(), eventFields.batchId(), alert == null ? null : alert.getBatchId(), rawRefFields.batchId()),
                firstNotBlank(eventFields.httpMethod(), rawRefFields.httpMethod()),
                firstNotBlank(eventFields.httpStatus(), rawRefFields.httpStatus()),
                firstNotBlank(event == null ? null : event.getRequestId(), eventFields.requestId(), rawRefFields.requestId()),
                firstNotBlank(eventFields.engine(), rawRefFields.engine())
        );
    }

    private SocExternalEvent linkedExternalEvent(SocAlert alert) {
        if (alert == null || alert.getId() == null) {
            return null;
        }
        List<SocExternalEvent> events = externalEventMapper.selectList(scopedExternalEventWrapper()
                .eq(SocExternalEvent::getAlertId, alert.getId())
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 1"));
        return events.isEmpty() ? null : events.get(0);
    }

    private DemoEvidenceFields evidenceFieldsFromRawRef(String rawRef) {
        if (!notBlank(rawRef)) {
            return DemoEvidenceFields.empty();
        }
        String batchId = null;
        int batchIndex = rawRef.indexOf("/batch/");
        if (batchIndex >= 0) {
            batchId = rawRef.substring(batchIndex + "/batch/".length());
        }
        String sourceType = rawRef.contains("/") ? rawRef.substring(0, rawRef.indexOf('/')) : null;
        return new DemoEvidenceFields(sourceType, null, null, null, null, null, null, null, null,
                trimToNull(batchId), null, null, null, null);
    }

    private DemoEvidenceFields evidenceFieldsFromJson(String json) {
        if (!notBlank(json)) {
            return DemoEvidenceFields.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return new DemoEvidenceFields(
                    firstNotBlank(text(root, "sourceType", null), text(root, "source_type", null)),
                    firstNotBlank(text(root, "eventType", null), text(root, "event_type", null)),
                    firstNotBlank(text(root, "ruleId", null), text(root, "rule_id", null)),
                    firstNotBlank(text(root, "ruleName", null), text(root, "rule_name", null), text(root, "rule", null), text(root, "message", null)),
                    firstNotBlank(text(root, "assetIp", null), text(root, "asset_ip", null), text(root, "dest_ip", null), text(root, "dst_ip", null)),
                    firstNotBlank(text(root, "targetUrl", null), text(root, "target_url", null), text(root, "url", null), text(root, "request_uri", null)),
                    text(root, "action", null),
                    firstNotBlank(text(root, "evidenceSummary", null), text(root, "evidence_summary", null), text(root, "summary", null)),
                    firstNotBlank(text(root, "demoCaseId", null), text(root, "demo_case_id", null)),
                    firstNotBlank(text(root, "batchId", null), text(root, "demoBatchId", null), text(root, "batch_id", null), text(root, "demo_batch_id", null)),
                    firstNotBlank(text(root, "httpMethod", null), text(root, "http_method", null), text(root, "method", null)),
                    firstNotBlank(text(root, "httpStatus", null), text(root, "http_status", null), text(root, "status", null)),
                    firstNotBlank(text(root, "requestId", null), text(root, "request_id", null), text(root, "id", null)),
                    text(root, "engine", null)
            );
        } catch (JsonProcessingException ignored) {
            return DemoEvidenceFields.empty();
        }
    }

    private String demoTicketSourceRemark(DemoEvidenceFields evidence) {
        if (evidence == null || (!notBlank(evidence.batchId()) && !notBlank(evidence.demoCaseId()))) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addRemarkPart(parts, "batchId", evidence.batchId());
        addRemarkPart(parts, "demoCaseId", evidence.demoCaseId());
        addRemarkPart(parts, "sourceType", evidence.sourceType());
        addRemarkPart(parts, "eventType", evidence.eventType());
        addRemarkPart(parts, "ruleId", evidence.ruleId());
        addRemarkPart(parts, "action", evidence.action());
        return "Demo Range 来源：" + String.join("，", parts);
    }

    private void addRemarkPart(List<String> parts, String label, String value) {
        if (notBlank(value)) {
            parts.add(label + "=" + value);
        }
    }

    private void dispatchNotification(String eventType, String severity, String targetType, Long targetId,
                                      String title, String content) {
        if (notificationService != null) {
            notificationService.dispatch(eventType, severity, targetType, targetId, title, content);
        }
    }

    private List<DetectionRuleCatalogEntry> detectionRuleCatalog() {
        return List.of(
                new DetectionRuleCatalogEntry("sigma", "SIGMA-DEMO-001", "Sigma suspicious process behavior", "medium", true, "sigma-1.0"),
                new DetectionRuleCatalogEntry("waf", "WAF-DEMO-1001", "Admin route protected by WAF policy", "high", true, "crs-demo-1.0"),
                new DetectionRuleCatalogEntry("waf", "WAF-DEMO-2001", "Unsafe upload metadata blocked by file policy", "high", true, "crs-demo-1.0"),
                new DetectionRuleCatalogEntry("zap", "10021", "ZAP security header finding", "medium", true, "zap-plugin"),
                new DetectionRuleCatalogEntry("suricata", "ET-SCAN-001", "ET SCAN Suspicious inbound port scan", "high", true, "et-open-demo"),
                new DetectionRuleCatalogEntry("suricata", "ET-POLICY-HTTP", "ET POLICY Unusual HTTP user agent", "medium", true, "et-open-demo"),
                new DetectionRuleCatalogEntry("wazuh", "5715", "Multiple authentication failures followed by success", "critical", true, "wazuh-rule"),
                new DetectionRuleCatalogEntry("wazuh", "5502", "Critical system configuration changed", "high", true, "wazuh-rule")
        );
    }

    private List<String> detectionRuleSourceTypes() {
        return List.of("sigma", "waf", "zap", "suricata", "wazuh", "zeek");
    }

    private List<String> detectionAlertSourceTypes() {
        return List.of("sigma", "waf", "zap", "suricata", "wazuh", "mock", "zeek");
    }

    private boolean matchesRuleFilter(DetectionRuleSummary item, SocPageRequest request) {
        boolean keywordMatched = !notBlank(request.keyword())
                || containsIgnoreCase(item.ruleId(), request.keyword())
                || containsIgnoreCase(item.ruleName(), request.keyword())
                || containsIgnoreCase(item.sourceType(), request.keyword());
        return keywordMatched
                && (!notBlank(request.sourceType()) || item.sourceType().equalsIgnoreCase(canonicalRuleSource(request.sourceType())))
                && (!notBlank(request.severity()) || item.severity().equalsIgnoreCase(request.severity()));
    }

    private String canonicalRuleSource(String sourceType) {
        if (!notBlank(sourceType)) {
            return null;
        }
        return "mock".equalsIgnoreCase(sourceType) ? "wazuh" : sourceType.toLowerCase(Locale.ROOT);
    }

    private String ruleKey(String sourceType, String ruleId) {
        return canonicalRuleSource(sourceType) + "::" + Objects.toString(ruleId, "").toLowerCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private String strongerSeverity(String current, String incoming) {
        String left = normalizeSeverity(firstNotBlank(current, "low"));
        String right = normalizeSeverity(firstNotBlank(incoming, left));
        return severityWeight(right) > severityWeight(left) ? right : left;
    }

    private int severityWeight(String severity) {
        return switch (normalizeSeverity(firstNotBlank(severity, "low"))) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private String normalizeSuricataEventType(String eventType, boolean alertEvent) {
        if (alertEvent) {
            return "ids_alert";
        }
        if ("http".equals(eventType)) {
            return "http_anomaly";
        }
        if ("dns".equals(eventType)) {
            return "dns_activity";
        }
        return eventType;
    }

    private String mapSuricataSeverity(int severity, String ruleName) {
        if (ruleName != null && ruleName.toLowerCase(Locale.ROOT).contains("exploit")) {
            return "critical";
        }
        if (severity <= 1) {
            return "high";
        }
        if (severity == 2) {
            return "medium";
        }
        return "low";
    }

    private LocalDateTime parseSuricataTime(String value) {
        if (!notBlank(value)) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (RuntimeException ignoredAgain) {
                return LocalDateTime.now();
            }
        }
    }

    private LocalDateTime parseAnyTime(String value) {
        if (!notBlank(value)) {
            return LocalDateTime.now();
        }
        try {
            if (value.matches("^\\d{10}(\\.\\d+)?$")) {
                return parseEpochOrNow(value);
            }
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (RuntimeException ignoredAgain) {
                return LocalDateTime.now();
            }
        }
    }

    private LocalDateTime parseEpochOrNow(String value) {
        if (!notBlank(value)) {
            return LocalDateTime.now();
        }
        try {
            long millis = Math.round(Double.parseDouble(value) * 1000);
            return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return LocalDateTime.now();
        }
    }

    private String normalizeSeverity(String value) {
        String normalized = Objects.toString(value, "medium").toLowerCase(Locale.ROOT);
        if (normalized.contains("critical") || normalized.contains("严重")) {
            return "critical";
        }
        if (normalized.contains("high") || normalized.contains("高")) {
            return "high";
        }
        if (normalized.contains("medium") || normalized.contains("中")) {
            return "medium";
        }
        return "low";
    }

    private int levelOf(String severity) {
        return switch (severity) {
            case "critical" -> 15;
            case "high" -> 12;
            case "medium" -> 8;
            default -> 3;
        };
    }

    private String tacticOf(String eventType) {
        return switch (eventType) {
            case "ids_alert" -> "Network Detection";
            case "http_anomaly", "dns_activity" -> "Command and Control";
            default -> "Collection";
        };
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text.isBlank() ? fallback : text;
    }

    private String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, "");
        return text.isBlank() ? null : text;
    }

    private String findText(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode current = root;
            for (String part : path.split("\\.")) {
                current = current.path(part);
            }
            if (!current.isMissingNode() && !current.isNull() && !current.asText().isBlank()) {
                return current.asText();
            }
        }
        return null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void validateWhitelistRequest(AlertWhitelistRequest request) {
        boolean hasMatcher = notBlank(request.ruleId()) || notBlank(request.assetIp())
                || notBlank(request.sourceIp()) || notBlank(request.severity());
        if (!hasMatcher) {
            throw new BusinessException("白名单至少需要填写规则 ID、资产 IP、来源 IP 或等级中的一项匹配条件");
        }
        if (notBlank(request.severity())) {
            validateStatus(request.severity(), "critical", "high", "medium", "low");
        }
    }

    private void applyWhitelistRequest(SocAlertWhitelist item, AlertWhitelistRequest request) {
        item.setRuleName(trimToNull(request.ruleName()));
        item.setRuleId(trimToNull(request.ruleId()));
        item.setAssetIp(trimToNull(request.assetIp()));
        item.setSourceIp(trimToNull(request.sourceIp()));
        item.setSeverity(trimToNull(request.severity()));
        item.setReason(trimToNull(request.reason()));
        item.setEnabled(request.enabled() == null ? 1 : (request.enabled() == 0 ? 0 : 1));
        item.setExpiresAt(request.expiresAt());
    }

    private void annotateAlertNoise(List<SocAlert> alerts) {
        for (SocAlert alert : alerts) {
            Optional<SocAlertWhitelist> matched = matchWhitelist(alert);
            alert.setWhitelistHit(matched.isPresent());
            alert.setWhitelistRuleName(matched.map(SocAlertWhitelist::getRuleName).orElse(null));
            alert.setNoiseStatus(matched.isPresent() ? "whitelisted" : alert.getStatus());
            alert.setRepeatCount(repeatCount(alert));
        }
    }

    private Optional<SocAlertWhitelist> matchWhitelist(SocAlert alert) {
        return whitelistMapper.selectList(scopedWhitelistWrapper()
                        .eq(SocAlertWhitelist::getEnabled, 1)
                        .and(w -> w.isNull(SocAlertWhitelist::getExpiresAt).or().ge(SocAlertWhitelist::getExpiresAt, LocalDateTime.now()))
                        .and(w -> w.isNull(SocAlertWhitelist::getRuleId).or().eq(SocAlertWhitelist::getRuleId, alert.getRuleId()))
                        .and(w -> w.isNull(SocAlertWhitelist::getAssetIp).or().eq(SocAlertWhitelist::getAssetIp, alert.getAssetIp()))
                        .and(w -> w.isNull(SocAlertWhitelist::getSourceIp).or().eq(SocAlertWhitelist::getSourceIp, alert.getSourceIp()))
                        .and(w -> w.isNull(SocAlertWhitelist::getSeverity).or().eq(SocAlertWhitelist::getSeverity, alert.getSeverity()))
                        .last("LIMIT 1"))
                .stream()
                .findFirst();
    }

    private long repeatCount(SocAlert alert) {
        LambdaQueryWrapper<SocAlert> wrapper = scopedAlertWrapper()
                .eq(SocAlert::getRuleId, alert.getRuleId())
                .eq(SocAlert::getAssetIp, alert.getAssetIp());
        if (alert.getSourceIp() == null) {
            wrapper.isNull(SocAlert::getSourceIp);
        } else {
            wrapper.eq(SocAlert::getSourceIp, alert.getSourceIp());
        }
        return alertMapper.selectCount(wrapper);
    }

    private List<SocAlert> recentScopedAlerts(int limit) {
        return alertMapper.selectList(scopedAlertWrapper()
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    private List<AlertAggregation> duplicateGroups(List<SocAlert> alerts) {
        Map<String, List<SocAlert>> grouped = new LinkedHashMap<>();
        for (SocAlert alert : alerts) {
            String key = alert.getRuleId() + "|" + alert.getAssetIp() + "|" + Objects.toString(alert.getSourceIp(), "");
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(alert);
        }
        return grouped.values().stream()
                .map(items -> {
                    SocAlert first = items.get(0);
                    return new AlertAggregation(first.getRuleId(), first.getRuleDescription(), first.getSeverity(),
                            first.getAssetName(), first.getAssetIp(), first.getSourceIp(), items.size(),
                            items.stream().map(SocAlert::getEventTime).max(LocalDateTime::compareTo).orElse(null),
                            matchWhitelist(first).map(SocAlertWhitelist::getRuleName).orElse(null));
                })
                .sorted(Comparator.comparing(AlertAggregation::repeatCount).reversed())
                .toList();
    }

    private AssetRiskScore assetRiskScore(SocAsset asset) {
        long highAlerts = alertMapper.selectCount(scopedAlertWrapper()
                .eq(SocAlert::getAssetIp, asset.getIp())
                .in(SocAlert::getSeverity, List.of("critical", "high"))
                .notIn(SocAlert::getStatus, List.of("closed", "ignored", "false_positive")));
        long mediumAlerts = alertMapper.selectCount(scopedAlertWrapper()
                .eq(SocAlert::getAssetIp, asset.getIp())
                .eq(SocAlert::getSeverity, "medium")
                .notIn(SocAlert::getStatus, List.of("closed", "ignored", "false_positive")));
        long openVulnerabilities = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper()
                .eq(SocVulnerability::getAssetIp, asset.getIp())
                .notIn(SocVulnerability::getStatus, List.of("fixed", "accepted")));
        long criticalHighVulnerabilities = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper()
                .eq(SocVulnerability::getAssetIp, asset.getIp())
                .in(SocVulnerability::getSeverity, List.of("critical", "high"))
                .notIn(SocVulnerability::getStatus, List.of("fixed", "accepted")));
        long failedBaselines = baselineMapper.selectCount(scopedBaselineWrapper()
                .eq(SocBaselineCheck::getAssetIp, asset.getIp())
                .eq(SocBaselineCheck::getResult, "failed"));
        long highFimEvents = fileIntegrityMapper.selectCount(scopedFileIntegrityWrapper()
                .eq(SocFileIntegrityEvent::getAssetIp, asset.getIp())
                .in(SocFileIntegrityEvent::getSeverity, List.of("critical", "high"))
                .notIn(SocFileIntegrityEvent::getStatus, List.of("confirmed", "ignored", "closed")));
        long highExternalEvents = externalEventMapper.selectCount(scopedExternalEventWrapper()
                .eq(SocExternalEvent::getAssetIp, asset.getIp())
                .in(SocExternalEvent::getSeverity, List.of("critical", "high"))
                .notIn(SocExternalEvent::getStatus, List.of("ignored", "closed")));
        long pendingTickets = ticketMapper.selectCount(scopedTicketWrapper()
                .eq(SocTicket::getDeptId, asset.getDeptId())
                .notIn(SocTicket::getStatus, List.of("已关闭", "已归档")));
        int alertWeight = capped((highAlerts * 12) + (mediumAlerts * 6), 32);
        int vulnerabilityWeight = capped((criticalHighVulnerabilities * 14) + ((openVulnerabilities - criticalHighVulnerabilities) * 6), 28);
        int baselineWeight = capped(failedBaselines * 8, 20);
        int exposureWeight = capped((highFimEvents * 6) + (highExternalEvents * 8), 20);
        int handlingWeight = capped(pendingTickets * 4, 12);
        int score = Math.min(100, riskLevelBase(asset.getRiskLevel()) + alertWeight + vulnerabilityWeight + baselineWeight + exposureWeight + handlingWeight);
        String explanation = "资产等级 " + asset.getRiskLevel()
                + "，未关闭高危告警 " + highAlerts
                + "，待修复漏洞 " + openVulnerabilities
                + "，失败基线 " + failedBaselines
                + "，高危外部/FIM 事件 " + (highExternalEvents + highFimEvents)
                + "，部门待处理工单 " + pendingTickets;
        return new AssetRiskScore(asset.getHostname(), asset.getIp(), asset.getDeptName(), asset.getRiskLevel(),
                score, explanation, alertWeight, vulnerabilityWeight, baselineWeight, exposureWeight, handlingWeight);
    }

    private AlertPriorityScore alertPriorityScore(SocAlert alert) {
        boolean highRiskAsset = assetMapper.selectCount(scopedAssetWrapper()
                .eq(SocAsset::getIp, alert.getAssetIp())
                .in(SocAsset::getRiskLevel, List.of("critical", "high"))) > 0;
        boolean iocHit = alert.getSourceIp() != null && externalEventMapper.selectCount(scopedExternalEventWrapper()
                .and(w -> w.eq(SocExternalEvent::getIoc, alert.getSourceIp())
                        .or().eq(SocExternalEvent::getSrcIp, alert.getSourceIp()))) > 0;
        long repeatCount = repeatCount(alert);
        int severityWeight = switch (alert.getSeverity()) {
            case "critical" -> 55;
            case "high" -> 42;
            case "medium" -> 28;
            default -> 12;
        };
        int repeatWeight = capped((repeatCount - 1) * 8, 24);
        int assetWeight = highRiskAsset ? 12 : 0;
        int iocWeight = iocHit ? 10 : 0;
        int statusWeight = List.of("new", "acknowledged", "ticketed").contains(alert.getStatus()) ? 6 : 0;
        int score = Math.min(100, severityWeight + repeatWeight + assetWeight + iocWeight + statusWeight);
        String reason = alert.getSeverity() + " 等级"
                + (highRiskAsset ? "，命中高风险资产" : "")
                + (iocHit ? "，来源 IP 命中外部事件 IOC" : "")
                + (repeatCount > 1 ? "，近期待处置重复 " + repeatCount + " 次" : "")
                + ("new".equals(alert.getStatus()) ? "，尚未确认" : "，状态 " + alert.getStatus());
        return new AlertPriorityScore(alert.getAlertUid(), alert.getSeverity(), alert.getRuleDescription(),
                alert.getAssetName(), alert.getAssetIp(), score, reason, repeatCount, iocHit, highRiskAsset,
                alert.getStatus(), alert.getEventTime());
    }

    private List<DepartmentRisk> departmentRisks(List<SocAsset> assets) {
        return assets.stream()
                .collect(java.util.stream.Collectors.groupingBy(asset -> Objects.toString(asset.getDeptId(), "0"),
                        LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .values()
                .stream()
                .map(items -> {
                    SocAsset first = items.get(0);
                    Long deptId = first.getDeptId();
                    String deptName = first.getDeptName() == null ? "未分配部门" : first.getDeptName();
                    long highAlerts = alertMapper.selectCount(scopedAlertWrapper()
                            .eq(deptId != null, SocAlert::getDeptId, deptId)
                            .in(SocAlert::getSeverity, List.of("critical", "high"))
                            .notIn(SocAlert::getStatus, List.of("closed", "ignored", "false_positive")));
                    long openVulnerabilities = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper()
                            .eq(deptId != null, SocVulnerability::getDeptId, deptId)
                            .notIn(SocVulnerability::getStatus, List.of("fixed", "accepted")));
                    long failedBaselines = baselineMapper.selectCount(scopedBaselineWrapper()
                            .eq(deptId != null, SocBaselineCheck::getDeptId, deptId)
                            .eq(SocBaselineCheck::getResult, "failed"));
                    long pendingTickets = ticketMapper.selectCount(scopedTicketWrapper()
                            .eq(deptId != null, SocTicket::getDeptId, deptId)
                            .notIn(SocTicket::getStatus, List.of("已关闭", "已归档")));
                    int score = Math.min(100, capped(highAlerts * 16, 40) + capped(openVulnerabilities * 10, 25)
                            + capped(failedBaselines * 8, 20) + capped(pendingTickets * 8, 15));
                    return new DepartmentRisk(deptName, items.size(), score, highAlerts, openVulnerabilities, failedBaselines, pendingTickets);
                })
                .sorted(Comparator.comparing(DepartmentRisk::score).reversed())
                .toList();
    }

    private OperationMetrics operationMetrics() {
        LocalDateTime now = LocalDateTime.now();
        long totalAlerts = alertMapper.selectCount(scopedAlertWrapper());
        long falsePositiveAlerts = alertMapper.selectCount(scopedAlertWrapper().eq(SocAlert::getStatus, "false_positive"));
        long pendingTickets = ticketMapper.selectCount(scopedTicketWrapper().notIn(SocTicket::getStatus, List.of("已关闭", "已归档")));
        long overdueTickets = ticketMapper.selectCount(scopedTicketWrapper()
                .notIn(SocTicket::getStatus, List.of("已关闭", "已归档"))
                .lt(SocTicket::getDueAt, now));
        List<SocTicket> closedTickets = ticketMapper.selectList(scopedTicketWrapper()
                .in(SocTicket::getStatus, List.of("已关闭", "已归档"))
                .isNotNull(SocTicket::getClosedAt));
        long slaMetTickets = closedTickets.stream()
                .filter(ticket -> ticket.getDueAt() == null || !ticket.getClosedAt().isAfter(ticket.getDueAt()))
                .count();
        long averageCloseHours = Math.round(closedTickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null && ticket.getClosedAt() != null)
                .mapToLong(ticket -> Math.max(1, Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toHours()))
                .average()
                .orElse(0));
        long duplicateGroups = duplicateGroups(recentScopedAlerts(500)).stream()
                .filter(item -> item.repeatCount() > 1)
                .count();
        long slaRate = closedTickets.isEmpty() ? 100 : Math.round((slaMetTickets * 100.0) / closedTickets.size());
        long falsePositiveRate = totalAlerts == 0 ? 0 : Math.round((falsePositiveAlerts * 100.0) / totalAlerts);
        return new OperationMetrics(pendingTickets, overdueTickets, closedTickets.size(), slaMetTickets,
                falsePositiveAlerts, duplicateGroups, slaRate, falsePositiveRate, averageCloseHours);
    }

    private List<SecurityTimelineItem> securityTimeline() {
        List<SecurityTimelineItem> timeline = new ArrayList<>();
        recentScopedAlerts(8).forEach(alert -> timeline.add(new SecurityTimelineItem(alert.getEventTime(), "告警",
                alert.getRuleDescription(), alert.getSeverity(), alert.getStatus(), alert.getAssetName(), null)));
        ticketMapper.selectList(scopedTicketWrapper().orderByDesc(SocTicket::getCreatedAt).last("LIMIT 6"))
                .forEach(ticket -> {
                    timeline.add(new SecurityTimelineItem(ticket.getCreatedAt(), "工单", ticket.getTitle(),
                            ticket.getSeverity(), ticket.getStatus(), null, ticket.getAssigneeName()));
                    if (ticket.getClosedAt() != null) {
                        timeline.add(new SecurityTimelineItem(ticket.getClosedAt(), "处置关闭", ticket.getTitle(),
                                ticket.getSeverity(), ticket.getStatus(), null, ticket.getAssigneeName()));
                    }
                });
        externalEventMapper.selectList(scopedExternalEventWrapper().orderByDesc(SocExternalEvent::getEventTime).last("LIMIT 6"))
                .forEach(event -> timeline.add(new SecurityTimelineItem(event.getEventTime(), "外部事件",
                        firstNotBlank(event.getRuleName(), event.getEventType()), event.getSeverity(),
                        event.getStatus(), event.getAssetName(), event.getSourceType())));
        return timeline.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(SecurityTimelineItem::occurredAt).reversed())
                .limit(12)
                .toList();
    }

    private int riskLevelBase(String riskLevel) {
        return switch (Objects.toString(riskLevel, "")) {
            case "critical" -> 35;
            case "high" -> 26;
            case "medium" -> 16;
            default -> 8;
        };
    }

    private int capped(long value, int max) {
        return (int) Math.min(max, Math.max(0, value));
    }

    private void closeLinkedAlert(SocTicket ticket) {
        if (ticket.getAlertId() == null) {
            return;
        }
        SocAlert alert = alertMapper.selectById(ticket.getAlertId());
        if (alert != null) {
            alert.setStatus("closed");
            alert.setClosedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
    }

    private void appendTimeline(Long ticketId, String action, String fromStatus, String toStatus, String remark) {
        SocTicketTimeline timeline = new SocTicketTimeline();
        timeline.setTicketId(ticketId);
        timeline.setAction(action);
        timeline.setFromStatus(fromStatus);
        timeline.setToStatus(toStatus);
        timeline.setOperatorName(securityScope.currentUsername());
        timeline.setRemark(remark);
        timelineMapper.insert(timeline);
    }

    private LambdaQueryWrapper<SocAlert> scopedAlertWrapper() {
        LambdaQueryWrapper<SocAlert> wrapper = new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getDeleted, 0)
                .and(w -> w.isNotNull(SocAlert::getAssetIp).ne(SocAlert::getAssetIp, "")
                        .or().isNotNull(SocAlert::getAssetName).ne(SocAlert::getAssetName, ""))
                .apply("EXISTS (SELECT 1 FROM soc_asset s WHERE s.deleted = 0 AND (s.ip = asset_ip OR s.hostname = asset_name))");
        applyRealSource(wrapper, SocAlert::getSourceType);
        securityScope.applyDataScope(wrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocAsset> scopedAssetWrapper() {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getDeleted, 0)
                .and(w -> w.isNotNull(SocAsset::getIp).ne(SocAsset::getIp, "")
                        .or().isNotNull(SocAsset::getHostname).ne(SocAsset::getHostname, ""));
        applyRealSource(wrapper, SocAsset::getSourceType);
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocTicket> scopedTicketWrapper() {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<SocTicket>()
                .eq(SocTicket::getDeleted, 0)
                .isNotNull(SocTicket::getAlertId)
                .apply("EXISTS (SELECT 1 FROM soc_alert a WHERE a.deleted = 0 AND a.id = alert_id AND a.source_type IS NOT NULL AND a.source_type NOT IN (" + NON_REAL_SOURCE_SQL + "))");
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocVulnerability> scopedVulnerabilityWrapper() {
        LambdaQueryWrapper<SocVulnerability> wrapper = new LambdaQueryWrapper<SocVulnerability>()
                .eq(SocVulnerability::getDeleted, 0)
                .and(w -> w.isNotNull(SocVulnerability::getAssetIp).ne(SocVulnerability::getAssetIp, "")
                        .or().isNotNull(SocVulnerability::getAssetName).ne(SocVulnerability::getAssetName, ""))
                .apply("EXISTS (SELECT 1 FROM soc_asset s WHERE s.deleted = 0 AND (s.ip = asset_ip OR s.hostname = asset_name))");
        applyRealSource(wrapper, SocVulnerability::getSourceType);
        securityScope.applyDataScope(wrapper, SocVulnerability::getOwnerId, SocVulnerability::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocBaselineCheck> scopedBaselineWrapper() {
        LambdaQueryWrapper<SocBaselineCheck> wrapper = new LambdaQueryWrapper<SocBaselineCheck>()
                .eq(SocBaselineCheck::getDeleted, 0)
                .and(w -> w.isNotNull(SocBaselineCheck::getAssetIp).ne(SocBaselineCheck::getAssetIp, "")
                        .or().isNotNull(SocBaselineCheck::getAssetName).ne(SocBaselineCheck::getAssetName, ""))
                .apply("EXISTS (SELECT 1 FROM soc_asset s WHERE s.deleted = 0 AND (s.ip = asset_ip OR s.hostname = asset_name))");
        applyRealSource(wrapper, SocBaselineCheck::getSourceType);
        securityScope.applyDataScope(wrapper, SocBaselineCheck::getOwnerId, SocBaselineCheck::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocFileIntegrityEvent> scopedFileIntegrityWrapper() {
        LambdaQueryWrapper<SocFileIntegrityEvent> wrapper = new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getDeleted, 0)
                .and(w -> w.isNotNull(SocFileIntegrityEvent::getAssetIp).ne(SocFileIntegrityEvent::getAssetIp, "")
                        .or().isNotNull(SocFileIntegrityEvent::getHostname).ne(SocFileIntegrityEvent::getHostname, ""))
                .apply("EXISTS (SELECT 1 FROM soc_asset s WHERE s.deleted = 0 AND (s.ip = asset_ip OR s.hostname = hostname))");
        applyRealSource(wrapper, SocFileIntegrityEvent::getSourceType);
        securityScope.applyDataScope(wrapper, SocFileIntegrityEvent::getOwnerId, SocFileIntegrityEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocExternalEvent> scopedExternalEventWrapper() {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .and(w -> w.isNotNull(SocExternalEvent::getAssetIp).ne(SocExternalEvent::getAssetIp, "")
                        .or().isNotNull(SocExternalEvent::getAssetName).ne(SocExternalEvent::getAssetName, "")
                        .or().isNotNull(SocExternalEvent::getDestIp).ne(SocExternalEvent::getDestIp, ""))
                .apply("EXISTS (SELECT 1 FROM soc_asset s WHERE s.deleted = 0 AND (s.ip = asset_ip OR s.hostname = asset_name OR s.ip = dest_ip))");
        applyRealSource(wrapper, SocExternalEvent::getSourceType);
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocAlertWhitelist> scopedWhitelistWrapper() {
        LambdaQueryWrapper<SocAlertWhitelist> wrapper = new LambdaQueryWrapper<SocAlertWhitelist>()
                .eq(SocAlertWhitelist::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocAlertWhitelist::getOwnerId, SocAlertWhitelist::getDeptId);
        return wrapper;
    }

    private <T> void applyRealSource(LambdaQueryWrapper<T> wrapper, SFunction<T, String> sourceColumn) {
        wrapper.isNotNull(sourceColumn).notIn(sourceColumn, NON_REAL_SOURCE_TYPES);
    }

    private void ensureAlertAccess(SocAlert alert) {
        if (!securityScope.canAccess(alert.getOwnerId(), alert.getDeptId())) {
            throw new BusinessException("无权访问该告警");
        }
    }

    private void ensureTicketAccess(SocTicket ticket) {
        if (!securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId())) {
            throw new BusinessException("无权访问该工单");
        }
    }

    private void ensureAccess(Long ownerId, Long deptId, String message) {
        if (!securityScope.canAccess(ownerId, deptId)) {
            throw new BusinessException(message);
        }
    }

    private void validateStatus(String status, String... allowed) {
        if (status == null || Arrays.stream(allowed).noneMatch(status::equals)) {
            throw new BusinessException("不支持的状态流转目标");
        }
    }

    private LocalDate defaultStart(String reportType, LocalDate end) {
        return switch (reportType) {
            case "weekly" -> end.minusDays(6);
            case "monthly" -> end.withDayOfMonth(1);
            default -> end;
        };
    }

    private String labelOf(String reportType) {
        return switch (reportType) {
            case "weekly" -> "周报";
            case "monthly" -> "月报";
            default -> "日报";
        };
    }

    private ReportMetrics reportMetrics(LocalDate start, LocalDate end) {
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        long alerts = alertMapper.selectCount(scopedAlertWrapper().ge(SocAlert::getEventTime, startTime).lt(SocAlert::getEventTime, endTime));
        long critical = alertMapper.selectCount(scopedAlertWrapper().ge(SocAlert::getEventTime, startTime).lt(SocAlert::getEventTime, endTime).eq(SocAlert::getSeverity, "critical"));
        long high = alertMapper.selectCount(scopedAlertWrapper().ge(SocAlert::getEventTime, startTime).lt(SocAlert::getEventTime, endTime).eq(SocAlert::getSeverity, "high"));
        long medium = alertMapper.selectCount(scopedAlertWrapper().ge(SocAlert::getEventTime, startTime).lt(SocAlert::getEventTime, endTime).eq(SocAlert::getSeverity, "medium"));
        long low = alertMapper.selectCount(scopedAlertWrapper().ge(SocAlert::getEventTime, startTime).lt(SocAlert::getEventTime, endTime).eq(SocAlert::getSeverity, "low"));
        long closedTickets = ticketMapper.selectCount(scopedTicketWrapper().ge(SocTicket::getCreatedAt, startTime).lt(SocTicket::getCreatedAt, endTime).in(SocTicket::getStatus, List.of("已关闭", "已归档")));
        long pendingTickets = ticketMapper.selectCount(scopedTicketWrapper().notIn(SocTicket::getStatus, List.of("已关闭", "已归档")));
        long highRiskAssets = assetMapper.selectCount(scopedAssetWrapper().in(SocAsset::getRiskLevel, List.of("critical", "high")));
        long allAssets = assetMapper.selectCount(scopedAssetWrapper());
        long criticalVulnerabilities = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper().eq(SocVulnerability::getSeverity, "critical"));
        long highVulnerabilities = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper().eq(SocVulnerability::getSeverity, "high"));
        long openVulnerabilities = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper().notIn(SocVulnerability::getStatus, List.of("fixed", "accepted")));
        long baselineChecks = baselineMapper.selectCount(scopedBaselineWrapper());
        long failedBaselines = baselineMapper.selectCount(scopedBaselineWrapper().eq(SocBaselineCheck::getResult, "failed"));
        long fimEvents = fileIntegrityMapper.selectCount(scopedFileIntegrityWrapper().ge(SocFileIntegrityEvent::getEventTime, startTime).lt(SocFileIntegrityEvent::getEventTime, endTime));
        long fimPendingReview = fileIntegrityMapper.selectCount(scopedFileIntegrityWrapper().notIn(SocFileIntegrityEvent::getStatus, List.of("confirmed", "ignored", "closed")));
        long highFimEvents = fileIntegrityMapper.selectCount(scopedFileIntegrityWrapper().in(SocFileIntegrityEvent::getSeverity, List.of("critical", "high")));
        long externalEvents = externalEventMapper.selectCount(scopedExternalEventWrapper().ge(SocExternalEvent::getEventTime, startTime).lt(SocExternalEvent::getEventTime, endTime));
        long highExternalEvents = externalEventMapper.selectCount(scopedExternalEventWrapper().ge(SocExternalEvent::getEventTime, startTime).lt(SocExternalEvent::getEventTime, endTime).in(SocExternalEvent::getSeverity, List.of("critical", "high")));
        String externalSources = externalEventSummary().stream().map(item -> item.sourceType() + "(" + item.total() + ")").reduce((left, right) -> left + ", " + right).orElse("-");
        String trend = alertTrend().stream().map(item -> item.date() + ":" + item.count()).reduce((left, right) -> left + ", " + right).orElse("-");
        String topAssets = affectedAssets().stream().map(item -> item.name() + "(" + item.value() + ")").reduce((left, right) -> left + ", " + right).orElse("-");
        String trendAnomalies = trendAnomalySummary();
        long baselinePassRate = baselineChecks == 0 ? 100 : Math.round(((baselineChecks - failedBaselines) * 100.0) / baselineChecks);
        return new ReportMetrics(alerts, critical, high, medium, low, closedTickets, pendingTickets,
                highRiskAssets, allAssets, trend, topAssets, criticalVulnerabilities, highVulnerabilities,
                openVulnerabilities, baselineChecks, failedBaselines, baselinePassRate, fimEvents,
                fimPendingReview, highFimEvents, externalEvents, highExternalEvents, externalSources,
                trendAnomalies);
    }

    private String reportSummary(ReportMetrics metrics) {
        return String.join("；",
                "告警趋势：" + metrics.trend(),
                "风险等级：critical " + metrics.critical() + "，high " + metrics.high() + "，medium " + metrics.medium() + "，low " + metrics.low(),
                "处置情况：本周期告警 " + metrics.alerts() + " 条，已关闭/归档工单 " + metrics.closedTickets() + " 个，待处理工单 " + metrics.pendingTickets() + " 个",
                "资产风险：受管资产 " + metrics.allAssets() + " 台，高风险资产 " + metrics.highRiskAssets() + " 台；受影响资产排行 " + metrics.topAssets(),
                "漏洞态势：critical " + metrics.criticalVulnerabilities() + " 个，high " + metrics.highVulnerabilities() + " 个，待修复 " + metrics.openVulnerabilities() + " 个",
                "基线核查：核查项 " + metrics.baselineChecks() + " 个，失败 " + metrics.failedBaselines() + " 个，通过率 " + metrics.baselinePassRate() + "%",
                "文件完整性：本周期事件 " + metrics.fimEvents() + " 条，待复核 " + metrics.fimPendingReview() + " 条，高危事件 " + metrics.highFimEvents() + " 条",
                "外部生态：本周期规范化事件 " + metrics.externalEvents() + " 条，高风险 " + metrics.highExternalEvents() + " 条；来源覆盖 " + metrics.externalSources(),
                "趋势异常：" + metrics.trendAnomalies(),
                operationMetricsSummary()
        );
    }

    private String reportRecommendation(ReportMetrics metrics) {
        List<String> suggestions = new ArrayList<>();
        if (metrics.critical() + metrics.high() > 0) {
            suggestions.add("优先处置 critical/high 告警并复核关联账号、进程和来源 IP。");
        }
        if (metrics.pendingTickets() > 0) {
            suggestions.add("推动待处理工单按 SLA 完成处理、复核、关闭和归档。");
        }
        if (metrics.highRiskAssets() > 0) {
            suggestions.add("对高风险资产执行补丁、基线、弱口令和暴露面整改复查。");
        }
        if (metrics.openVulnerabilities() > 0) {
            suggestions.add("优先修复 critical/high 漏洞，无法立即修复的记录接受风险依据和缓解措施。");
        }
        if (metrics.failedBaselines() > 0) {
            suggestions.add("按 SSH、密码策略、防火墙、系统服务和敏感文件权限分类推进基线失败项整改。");
        }
        if (metrics.fimPendingReview() + metrics.highFimEvents() > 0) {
            suggestions.add("复核文件完整性高危变更，关联变更单、登录来源和进程证据。");
        }
        if (metrics.highExternalEvents() > 0) {
            suggestions.add("对 Suricata 等外部来源的高风险事件执行统一告警关联、资产归属确认和处置闭环。");
        }
        if (!"-".equals(metrics.trendAnomalies())) {
            suggestions.add("复核趋势异常 Top 项，优先处理数量突增、跨源同时上升和严重级别占比升高的资产。");
        }
        suggestions.add("持续复盘误报规则和白名单，降低重复告警噪声。");
        return String.join("；", suggestions);
    }

    private String trendAnomalySummary() {
        try {
            return trendAnomalyService.topAnomalies(5).stream()
                    .map(item -> item.title() + "(" + item.currentCount() + "/" + item.baselineCount() + ", score " + item.anomalyScore() + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("-");
        } catch (Exception ignored) {
            return "-";
        }
    }

    private String operationMetricsSummary() {
        try {
            if (operationsService == null) {
                return "运营指标：暂不可用";
            }
            return operationsService.reportSummaryLine();
        } catch (Exception ignored) {
            return "运营指标：暂不可用";
        }
    }

    private SocOperationsService.OperationsOverview operationsOverview() {
        try {
            return operationsService == null ? null : operationsService.overview();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<SocIncidentCluster> validationIncidents(String batchId) {
        if (incidentClusterMapper == null || batchId == null || batchId.isBlank()) {
            return List.of();
        }
        LambdaQueryWrapper<SocIncidentCluster> wrapper = new LambdaQueryWrapper<SocIncidentCluster>()
                .eq(SocIncidentCluster::getDeleted, 0)
                .and(w -> w.eq(SocIncidentCluster::getBatchId, batchId)
                        .or().like(SocIncidentCluster::getCorrelationKey, batchId)
                        .or().like(SocIncidentCluster::getSummary, batchId)
                        .or().like(SocIncidentCluster::getTitle, batchId))
                .orderByDesc(SocIncidentCluster::getScore)
                .orderByDesc(SocIncidentCluster::getUpdatedAt)
                .last("LIMIT 20");
        securityScope.applyDataScope(wrapper, SocIncidentCluster::getOwnerId, SocIncidentCluster::getDeptId);
        return incidentClusterMapper.selectList(wrapper);
    }

    private String validationBusinessConclusion(DemoRangeEvidenceChain chain,
                                                List<SocIncidentCluster> incidents,
                                                SocOperationsService.TopRiskAsset topRiskAsset,
                                                PlaybookTaskMetrics taskMetrics,
                                                SocOperationsService.ClientTaskMetrics clientTaskMetrics) {
        String assetName = topRiskAsset == null
                ? firstNotBlank(
                chain.events().stream().map(event -> firstNotBlank(event.getAssetName(), event.getAssetIp())).filter(Objects::nonNull).findFirst().orElse(null),
                chain.alerts().stream().map(alert -> firstNotBlank(alert.getAssetName(), alert.getAssetIp())).filter(Objects::nonNull).findFirst().orElse(null),
                chain.vulnerabilities().stream().map(item -> firstNotBlank(item.getAssetName(), item.getAssetIp())).filter(Objects::nonNull).findFirst().orElse(null),
                "当前演示资产")
                : firstNotBlank(topRiskAsset.hostname(), topRiskAsset.assetIp(), "当前演示资产");
        String riskLevel = topRiskAsset == null ? "待评估" : firstNotBlank(topRiskAsset.riskLevel(), "高风险");
        long pendingTickets = Math.max(0, chain.summary().ticketCount() - taskMetrics.completedTasks());
        long pendingEmployeeTasks = clientTaskMetrics == null
                ? taskMetrics.employeeTasks()
                : Math.max(0, clientTaskMetrics.totalTasks() - clientTaskMetrics.completedTasks());
        return assetName + " 当前为 " + riskLevel
                + "，主要原因是 " + incidents.size() + " 个事件簇、" + pendingTickets + " 个待推进工单和 "
                + pendingEmployeeTasks + " 个员工待办仍需闭环。建议优先应用 Web 网关阻断处置剧本并完成本机检查。";
    }

    private String topRiskAssetSummary(SocOperationsService.TopRiskAsset asset) {
        if (asset == null) {
            return "暂无高风险资产";
        }
        return firstNotBlank(asset.hostname(), asset.assetIp(), "未知资产")
                + "(" + firstNotBlank(asset.assetIp(), "-") + ", score " + asset.riskScore() + ", " + firstNotBlank(asset.riskLevel(), "-") + ")";
    }

    private String recommendationSummary(SocOperationsService.RecommendationAdoptionMetrics metrics) {
        if (metrics == null) {
            return "暂不可用";
        }
        return metrics.totalRecommendations() + " 个，已采纳 " + metrics.adoptedRecommendations()
                + " 个，采纳率 " + metrics.adoptionRate() + "%";
    }

    private String clientTaskSummary(SocOperationsService.ClientTaskMetrics metrics) {
        if (metrics == null) {
            return "暂不可用";
        }
        return metrics.completedTasks() + "/" + metrics.totalTasks()
                + "，完成率 " + metrics.completionRate() + "%"
                + "，体检覆盖率 " + metrics.checkupCoverageRate() + "%"
                + "，逾期 " + metrics.overdueTasks() + " 个";
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String signed(long value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private List<List<String>> reportRows(SocReport report) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("基础信息", "报表编号", report.getReportNo()));
        rows.add(List.of("基础信息", "报表类型", report.getReportType()));
        rows.add(List.of("基础信息", "统计周期", report.getPeriodStart() + " ~ " + report.getPeriodEnd()));
        for (String line : splitReportText(report.getSummary())) {
            String[] parts = line.split("：", 2);
            rows.add(List.of("统计内容", parts[0], parts.length > 1 ? parts[1] : line));
        }
        for (String line : splitReportText(report.getRecommendation())) {
            rows.add(List.of("整改建议", "建议", line));
        }
        return rows;
    }

    private List<String> reportPreviewLines(SocReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("报表编号：" + report.getReportNo());
        lines.add("报表类型：" + report.getReportType());
        lines.add("统计周期：" + report.getPeriodStart() + " ~ " + report.getPeriodEnd());
        lines.add("生成时间：" + (report.getGeneratedAt() == null ? "-" : report.getGeneratedAt()));
        lines.add("状态：" + firstNotBlank(report.getStatus(), "-"));
        lines.add("摘要：");
        lines.addAll(splitReportText(report.getSummary()));
        lines.add("整改建议：");
        lines.addAll(splitReportText(report.getRecommendation()));
        return lines;
    }

    public String reportExportFilename(SocReport report, String format) {
        String extension = "pdf".equalsIgnoreCase(format) ? "pdf" : "xlsx";
        String no = firstNotBlank(report.getReportNo(), "report-" + report.getId())
                .replaceAll("[^A-Za-z0-9._-]", "-");
        return no + "." + extension;
    }

    private SocReport reportById(Long id) {
        SocReport report = reportMapper.selectById(id);
        if (report == null || Integer.valueOf(1).equals(report.getDeleted())) {
            throw new BusinessException("报表不存在");
        }
        return report;
    }

    private List<String> splitReportText(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[；\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private SocAsset accessibleAssetByIp(String ip) {
        SocAsset asset = assetMapper.selectOne(scopedAssetWrapper()
                .eq(SocAsset::getIp, ip)
                .last("LIMIT 1"));
        if (asset == null) {
            throw new BusinessException("电脑不存在或无权访问");
        }
        return asset;
    }

    private SocAsset localDemoAssetByIp(String ip) {
        SocAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, ip)
                .last("LIMIT 1"));
        if (asset != null) {
            return asset;
        }
        SocAsset demoAsset = new SocAsset();
        demoAsset.setHostname("local-demo-" + ip.replaceAll("[^0-9A-Za-z.-]", "-"));
        demoAsset.setIp(ip);
        demoAsset.setOsType("local-demo");
        demoAsset.setRiskLevel("medium");
        demoAsset.setSourceType("client-local");
        demoAsset.setDeptName("客户演示");
        demoAsset.setOwnerName("local-demo-client");
        demoAsset.setOpenAlertCount(0);
        return demoAsset;
    }

    private void ensureLoopbackClient(String remoteAddr) {
        String value = Objects.toString(remoteAddr, "");
        if (!List.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "localhost").contains(value)) {
            throw new BusinessException("本机演示终端接口仅允许从 127.0.0.1 调用");
        }
    }

    private int riskBase(String riskLevel) {
        return switch (Objects.toString(riskLevel, "low")) {
            case "critical" -> 82;
            case "high" -> 68;
            case "medium" -> 46;
            default -> 24;
        };
    }

    private List<ClientEvidenceItem> clientEvidenceTimeline(List<SocAlert> alerts,
                                                            List<SocVulnerability> vulnerabilities,
                                                            List<SocBaselineCheck> baselines,
                                                            List<SocFileIntegrityEvent> fileIntegrityEvents,
                                                            List<SocExternalEvent> externalEvents) {
        List<ClientEvidenceItem> items = new ArrayList<>();
        alerts.forEach(item -> items.add(new ClientEvidenceItem("alert-" + item.getId(), "告警", item.getRuleDescription(),
                item.getSeverity(), item.getStatus(), item.getEventTime(), firstNotBlank(item.getSourceIp(), item.getRawRef(), "-"))));
        vulnerabilities.forEach(item -> items.add(new ClientEvidenceItem("vulnerability-" + item.getId(), "漏洞", item.getCveId(),
                item.getSeverity(), item.getStatus(), item.getDetectedAt(), item.getSoftwareName() + " " + Objects.toString(item.getSoftwareVersion(), ""))));
        baselines.forEach(item -> items.add(new ClientEvidenceItem("baseline-" + item.getId(), "基线", item.getCheckItem(),
                item.getSeverity(), item.getStatus(), item.getCheckedAt(), item.getRemediation())));
        fileIntegrityEvents.forEach(item -> items.add(new ClientEvidenceItem("fim-" + item.getId(), "FIM", item.getFilePath(),
                item.getSeverity(), item.getStatus(), item.getEventTime(), item.getRuleName())));
        externalEvents.forEach(item -> items.add(new ClientEvidenceItem("external-" + item.getId(), "演练/多源事件", firstNotBlank(item.getRuleName(), item.getEventType()),
                item.getSeverity(), item.getStatus(), item.getEventTime(), firstNotBlank(item.getSrcIp(), "-") + " -> " + firstNotBlank(item.getDestIp(), "-"))));
        return items.stream()
                .sorted(Comparator.comparing(ClientEvidenceItem::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(30)
                .toList();
    }

    private LabAction labAction(String actionType) {
        return switch (actionType) {
            case "login_failure" -> new LabAction("登录失败观察", "auth_anomaly", "medium", "Credential Access", "127.0.0.1", true);
            case "sensitive_path" -> new LabAction("敏感路径访问拦截", "web_sensitive_path", "high", "Discovery", "/admin", true);
            case "upload_probe" -> new LabAction("异常上传入口触发", "web_upload_probe", "high", "Initial Access", "upload", true);
            case "privilege_boundary" -> new LabAction("权限边界访问拦截", "privilege_boundary", "critical", "Privilege Escalation", "role=admin", true);
            case "data_query" -> new LabAction("业务数据查询观察", "data_access_pattern", "medium", "Collection", "query", true);
            case "persistence_signal" -> new LabAction("持久化迹象观察", "persistence_signal", "high", "Persistence", "startup", true);
            default -> throw new BusinessException("不支持的演练动作");
        };
    }

    private String resolveTerminalOs(String requestedOs, SocAsset asset) {
        return LocalCheckPolicyService.normalizeOsType(firstNotBlank(requestedOs, asset.getOsType(), System.getProperty("os.name", "Linux")));
    }

    private TerminalCommandSpec terminalCommandSpec(LocalCheckPolicyService.ResolvedCommand command) {
        return new TerminalCommandSpec(command.commandKey(), command.displayName(), command.argv(),
                command.displayCommand(), command.severity(), command.timeoutSeconds(), command.outputLimitKb());
    }

    private TerminalExecution executeTerminalCommand(TerminalCommandSpec spec) {
        return executeTerminalCommand(spec.argv(), spec.timeoutSeconds(), spec.outputLimitKb());
    }

    private String normalizeWafEventType(String eventType, String action) {
        String normalized = Objects.toString(eventType, "").toLowerCase(Locale.ROOT).replace('-', '_');
        if (List.of("waf_block", "waf_detect", "upload_block", "api_abuse_block").contains(normalized)) {
            return normalized;
        }
        String actionValue = Objects.toString(action, "").toLowerCase(Locale.ROOT);
        if (actionValue.contains("block") || actionValue.contains("deny")) {
            return "waf_block";
        }
        return "waf_detect";
    }

    private String wafRuleName(String eventType, String action) {
        return switch (eventType) {
            case "upload_block" -> "WAF upload policy blocked demo file transfer";
            case "api_abuse_block" -> "WAF API abuse rate policy blocked demo request";
            case "waf_block" -> "WAF blocked demo request";
            default -> "WAF detected demo request";
        } + " (" + firstNotBlank(action, "detect") + ")";
    }

    private TerminalExecution executeTerminalCommand(List<String> argv, int timeoutSeconds, int outputLimitKb) {
        Process process = null;
        try {
            process = new ProcessBuilder(argv)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new TerminalExecution(List.of("命令超过 " + Math.max(1, timeoutSeconds) + " 秒限制，已终止。"), -1, true);
            }
            List<String> output = readProcessOutput(process, outputLimitKb);
            return new TerminalExecution(output.isEmpty() ? List.of("(无输出)") : output, process.exitValue(), false);
        } catch (IOException ex) {
            return new TerminalExecution(List.of("候选命令不可用 " + String.join(" ", argv) + "：" + ex.getMessage()), -1, false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new TerminalExecution(List.of("命令执行被中断。"), -1, true);
        }
    }

    private List<String> readProcessOutput(Process process, int outputLimitKb) throws IOException {
        List<String> lines = new ArrayList<>();
        int maxLength = Math.max(1, outputLimitKb) * 1024;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int totalLength = 0;
            while ((line = reader.readLine()) != null && lines.size() < 80 && totalLength < maxLength) {
                String sanitized = line.replaceAll("(?i)(password|token|secret|key)=\\S+", "$1=***");
                lines.add(sanitized);
                totalLength += sanitized.length();
            }
        }
        return lines;
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("serialize lab event failed", ex);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safeIpCandidate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() > 45) {
            return null;
        }
        if (trimmed.contains("://") || trimmed.contains("/") || trimmed.contains("?") || trimmed.contains("#")) {
            return null;
        }
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        boolean ipLike = trimmed.chars().allMatch(ch -> Character.isDigit(ch)
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F')
                || ch == '.'
                || ch == ':');
        return hasDigit && ipLike ? trimmed : null;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public record DashboardOverview(long todayAlerts, long highAlerts, long pendingTickets, long assets, long unhandledAlerts) {
    }

    public record TrendItem(String date, long count) {
    }

    public record NameValue(String name, long value) {
    }

    public record RiskAnalytics(List<AssetRiskScore> assetRisks, List<AlertPriorityScore> alertPriorities,
                                List<DepartmentRisk> departmentRisks, OperationMetrics operationMetrics,
                                List<SecurityTimelineItem> eventTimeline) {
    }

    public record AssetRiskScore(String hostname, String ip, String deptName, String riskLevel, int score,
                                 String explanation, int alertWeight, int vulnerabilityWeight, int baselineWeight,
                                 int exposureWeight, int handlingWeight) {
    }

    public record AlertPriorityScore(String alertUid, String severity, String ruleDescription, String assetName,
                                     String assetIp, int score, String reason, long repeatCount, boolean iocHit,
                                     boolean highRiskAsset, String status, LocalDateTime eventTime) {
    }

    public record DepartmentRisk(String deptName, long assets, int score, long highAlerts,
                                 long openVulnerabilities, long failedBaselines, long pendingTickets) {
    }

    public record OperationMetrics(long pendingTickets, long overdueTickets, long closedTickets,
                                   long slaMetTickets, long falsePositiveAlerts, long duplicateGroups,
                                   long slaRate, long falsePositiveRate, long averageCloseHours) {
    }

    public record SecurityTimelineItem(LocalDateTime occurredAt, String type, String title, String severity,
                                       String status, String assetName, String operatorName) {
    }

    public record ClientDeviceMetrics(int riskScore, long alerts, long openVulnerabilities, long failedBaselines,
                                      long pendingFileIntegrity, long pendingExternalEvents, String summary) {
    }

    public record ClientEvidenceItem(String id, String type, String title, String severity, String status,
                                     LocalDateTime occurredAt, String description) {
    }

    public record ClientDeviceProfile(SocAsset asset, ClientDeviceMetrics metrics, List<SocAlert> alerts,
                                      List<SocVulnerability> vulnerabilities, List<SocBaselineCheck> baselines,
                                      List<SocFileIntegrityEvent> fileIntegrityEvents,
                                      List<SocExternalEvent> externalEvents, List<ClientEvidenceItem> timeline) {
    }

    public record ClientLabEventResult(SocExternalEvent event, Long alertId, String message) {
    }

    public record ClientTerminalCommandResult(String commandKey, String command, List<String> output,
                                              int exitCode, boolean timeout, SocExternalEvent event,
                                              Long alertId) {
    }

    public record ClientSecuritySnapshotResult(String snapshotId, List<ClientSnapshotSection> sections,
                                               SocExternalEvent event, Long alertId, String message) {
    }

    public record ClientSnapshotSection(String key, String label, String command, List<String> output,
                                        int exitCode, boolean timeout, String severity) {
    }

    public record TicketDetail(SocTicket ticket, List<SocTicketTimeline> timeline, List<SocTicketTask> tasks) {
    }

    private PlaybookTaskMetrics playbookTaskMetrics(List<SocTicket> tickets) {
        List<Long> ticketIds = tickets == null ? List.of() : tickets.stream()
                .map(SocTicket::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ticketIds.isEmpty()) {
            return new PlaybookTaskMetrics(0, 0, 0);
        }
        List<SocTicketTask> tasks = ticketTaskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .in(SocTicketTask::getTicketId, ticketIds)
                .eq(SocTicketTask::getDeleted, 0));
        long completed = tasks.stream()
                .filter(task -> List.of("completed", "confirmed").contains(task.getStatus()))
                .count();
        long employee = tasks.stream()
                .filter(task -> "employee".equals(task.getAssigneeType()))
                .count();
        return new PlaybookTaskMetrics(tasks.size(), completed, employee);
    }

    private record PlaybookTaskMetrics(long totalTasks, long completedTasks, long employeeTasks) {
    }

    public record AlertNoiseSummary(long activeWhitelists, long whitelistHits, long falsePositiveAlerts,
                                    long duplicateGroups) {
    }

    public record AlertAggregation(String ruleId, String ruleDescription, String severity, String assetName,
                                   String assetIp, String sourceIp, long repeatCount, LocalDateTime latestEventTime,
                                   String whitelistRuleName) {
    }

    public record ExternalSourceSummary(String sourceType, long total, long highRisk, long linkedAlerts) {
    }

    public record DetectionRuleSummary(String ruleId, String ruleName, String sourceType, String severity,
                                       boolean enabled, String version, LocalDateTime lastHitAt,
                                       long hitCount, long falsePositiveCount) {
    }

    public record DetectionRuleHits(String sourceType, String ruleId,
                                    List<SocExternalEvent> events, List<SocAlert> alerts) {
    }

    public record AdapterFieldMapping(String adapter, String sourceField, String normalizedField,
                                      String requirement, String severityMapping, String dedupKey,
                                      String alertLinkRule, String sampleFile, String failureCase) {
    }

    public record SuricataImportResult(int importedEvents, int createdEvents, int updatedEvents, int linkedAlerts,
                                       int skippedLines, List<String> errors) {
    }

    public record CyberFusionImportResult(String sourceType, int importedEvents, int createdEvents,
                                          int updatedEvents, int linkedAlerts, int importedVulnerabilities,
                                          int skippedLines, List<String> errors) {
    }

    public record DemoRangeBatchImportResult(String batchId, int importedEvents, int createdAlerts,
                                             int createdVulnerabilities, int skippedItems, int failedItems,
                                             int updatedEvents, List<DemoRangeSourceImportResult> sources,
                                             String dedupRule, List<String> errors) {
    }

    public record DemoDataOperationResult(String seedBatchId, String demoRangeBatchId, int totalDemoRows,
                                          int deletedRowsBeforeImport, int importedRangeEvents,
                                          int createdRangeAlerts, int createdRangeVulnerabilities,
                                          int updatedRangeEvents, String message, List<String> errors) {
    }

    public record DemoDataStatus(String seedBatchId, String demoRangeBatchId, int totalDemoRows,
                                 boolean hasDemoData, String message) {
    }

    public record DemoRangeSourceImportResult(String sourceType, int importedEvents, int createdEvents,
                                              int updatedEvents, int linkedAlerts, int importedVulnerabilities,
                                              int skippedItems, List<String> errors) {
    }

    public record DemoRangeEvidenceChain(DemoRangeChainSummary summary,
                                         List<SocExternalEvent> events,
                                         List<SocAlert> alerts,
                                         List<SocVulnerability> vulnerabilities,
                                         List<SocTicket> tickets,
                                         List<SocReport> reports,
                                         List<SocNotificationLog> notificationLogs) {
    }

    public record DemoRangeChainSummary(String batchId, int eventCount, int alertCount,
                                        int vulnerabilityCount, long blockedCount, int ticketCount,
                                        int reportCount, int notificationLogCount, String sourceCoverage) {
    }

    public record CyberChefAnalysis(String fieldName, List<String> suggestedOperations,
                                    Map<String, String> findings, String note) {
    }

    public record AutomationDemoResult(String adapter, String status, String message) {
    }

    private record UpsertResult(boolean created) {
    }

    private record DemoRangeSourceSample(String sourceType, String content) {
    }

    private record DetectionRuleCatalogEntry(String sourceType, String ruleId, String ruleName,
                                             String severity, boolean enabled, String version) {
    }

    private static class MutableDetectionRule {
        private final String ruleId;
        private String ruleName;
        private final String sourceType;
        private String severity;
        private final boolean enabled;
        private final String version;
        private LocalDateTime lastHitAt;
        private long hitCount;
        private long falsePositiveCount;

        private MutableDetectionRule(String ruleId, String ruleName, String sourceType, String severity,
                                     boolean enabled, String version, LocalDateTime lastHitAt,
                                     long hitCount, long falsePositiveCount) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.sourceType = sourceType;
            this.severity = severity;
            this.enabled = enabled;
            this.version = version;
            this.lastHitAt = lastHitAt;
            this.hitCount = hitCount;
            this.falsePositiveCount = falsePositiveCount;
        }

        private DetectionRuleSummary toSummary() {
            return new DetectionRuleSummary(ruleId, ruleName, sourceType, severity, enabled, version,
                    lastHitAt, hitCount, falsePositiveCount);
        }
    }

    private record DemoEvidenceFields(String sourceType, String eventType, String ruleId, String ruleName,
                                      String assetIp, String targetUrl, String action, String evidenceSummary,
                                      String demoCaseId, String batchId, String httpMethod, String httpStatus,
                                      String requestId, String engine) {
        private static DemoEvidenceFields empty() {
            return new DemoEvidenceFields(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    private record NormalizedSuricataEvent(String eventUid, String eventType, String severity, String ruleId,
                                           String ruleName, String srcIp, String destIp, String assetName,
                                           String assetIp, String ioc, String rawEvent, String normalizedEvent,
                                           Long ownerId, Long deptId, LocalDateTime eventTime, boolean alertEvent) {
    }

    private record NormalizedCyberFusionEvent(String sourceType, String eventUid, String eventType, String severity,
                                              String ruleId, String ruleName, String srcIp, String destIp,
                                              String assetName, String assetIp, String ioc, String rawEvent,
                                              String normalizedEvent, Long ownerId, Long deptId,
                                              LocalDateTime eventTime, boolean alertEvent) {
    }

    private record ReportMetrics(long alerts, long critical, long high, long medium, long low, long closedTickets,
                                 long pendingTickets, long highRiskAssets, long allAssets, String trend, String topAssets,
                                 long criticalVulnerabilities, long highVulnerabilities, long openVulnerabilities,
                                 long baselineChecks, long failedBaselines, long baselinePassRate,
                                 long fimEvents, long fimPendingReview, long highFimEvents,
                                 long externalEvents, long highExternalEvents, String externalSources,
                                 String trendAnomalies) {
    }

    private record LabAction(String title, String eventType, String severity, String tactic, String ioc, boolean createAlert) {
    }

    private record TerminalCommandSpec(String commandKey, String label, List<String> argv, String displayCommand,
                                       String severity, int timeoutSeconds, int outputLimitKb) {
    }

    private record TerminalExecution(List<String> output, int exitCode, boolean timeout) {
    }
}
