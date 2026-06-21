package com.zhangjiyan.template.soc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.excel.ExcelExportUtils;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.alert.AlertActionRequest;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheck;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.dto.SocStatusRequest;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.external.SuricataImportRequest;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEvent;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import com.zhangjiyan.template.soc.noise.AlertWhitelistRequest;
import com.zhangjiyan.template.soc.noise.SocAlertWhitelist;
import com.zhangjiyan.template.soc.noise.SocAlertWhitelistMapper;
import com.zhangjiyan.template.soc.notification.SocNotificationService;
import com.zhangjiyan.template.soc.report.ReportGenerateRequest;
import com.zhangjiyan.template.soc.report.SocReport;
import com.zhangjiyan.template.soc.report.SocReportMapper;
import com.zhangjiyan.template.soc.settings.SocSyncTask;
import com.zhangjiyan.template.soc.settings.SocSyncTaskMapper;
import com.zhangjiyan.template.soc.settings.SocWazuhConfig;
import com.zhangjiyan.template.soc.settings.SocWazuhConfigMapper;
import com.zhangjiyan.template.soc.ticket.*;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TicketStateMachine ticketStateMachine;
    private final SocSecurityScope securityScope;
    private final SocNotificationService notificationService;
    private final ObjectMapper objectMapper;

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
        LambdaQueryWrapper<SocAlert> wrapper = new LambdaQueryWrapper<SocAlert>()
                .and(notBlank(request.keyword()), w -> w.like(SocAlert::getRuleDescription, request.keyword())
                        .or().like(SocAlert::getAssetName, request.keyword())
                        .or().like(SocAlert::getSourceIp, request.keyword()))
                .eq(notBlank(request.severity()), SocAlert::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocAlert::getStatus, request.status())
                .orderByDesc(SocAlert::getEventTime);
        securityScope.applyDataScope(wrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
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
        ticket.setTicketNo("INC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + alert.getId());
        ticket.setAlertId(alert.getId());
        ticket.setTitle(alert.getSeverity().toUpperCase(Locale.ROOT) + " 告警处置：" + alert.getRuleDescription());
        ticket.setSeverity(alert.getSeverity());
        ticket.setStatus("待分派");
        ticket.setAssigneeId(request.assigneeId());
        ticket.setDeptId(alert.getDeptId());
        ticket.setDueAt(LocalDateTime.now().plusHours("critical".equals(alert.getSeverity()) ? 4 : 24));
        ticketMapper.insert(ticket);
        appendTimeline(ticket.getId(), "转工单", null, "待分派", request.remark());
        alert.setTicketId(ticket.getId());
        alert.setStatus("ticketed");
        alertMapper.updateById(alert);
        notificationService.dispatch("alert_ticketed", alert.getSeverity(), "alert", alert.getId(),
                "告警已转工单：" + alert.getAlertUid(),
                alert.getSeverity().toUpperCase(Locale.ROOT) + " 告警已进入工单处置流程，工单号：" + ticket.getTicketNo());
        return ticket;
    }

    public PageResult<SocAsset> assets(SocPageRequest request) {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<SocAsset>()
                .and(notBlank(request.keyword()), w -> w.like(SocAsset::getHostname, request.keyword()).or().like(SocAsset::getIp, request.keyword()))
                .eq(notBlank(request.riskLevel()), SocAsset::getRiskLevel, request.riskLevel())
                .orderByDesc(SocAsset::getOpenAlertCount)
                .orderByDesc(SocAsset::getLastSeenAt);
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return PageResult.from(assetMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    public PageResult<SocTicket> tickets(SocPageRequest request) {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<SocTicket>()
                .and(notBlank(request.keyword()), w -> w.like(SocTicket::getTicketNo, request.keyword()).or().like(SocTicket::getTitle, request.keyword()))
                .eq(notBlank(request.status()), SocTicket::getStatus, request.status())
                .eq(notBlank(request.severity()), SocTicket::getSeverity, request.severity())
                .orderByDesc(SocTicket::getCreatedAt);
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
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
        return new TicketDetail(ticket, timeline);
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
            notificationService.dispatch("ticket_closed", ticket.getSeverity(), "ticket", ticket.getId(),
                    "工单已关闭：" + ticket.getTicketNo(),
                    ticket.getTitle() + " 已完成处置并关闭。");
        }
        if ("已归档".equals(request.targetStatus())) {
            ticket.setArchivedAt(LocalDateTime.now());
        }
        if ("待复核".equals(request.targetStatus())) {
            notificationService.dispatch("ticket_review", ticket.getSeverity(), "ticket", ticket.getId(),
                    "工单待复核：" + ticket.getTicketNo(),
                    ticket.getTitle() + " 已提交复核，请按 SOC 流程确认处置结论。");
        }
        ticketMapper.updateById(ticket);
        appendTimeline(ticket.getId(), "状态流转", fromStatus, request.targetStatus(), request.remark());
        return ticket;
    }

    public PageResult<SocReport> reports(SocPageRequest request) {
        LambdaQueryWrapper<SocReport> wrapper = new LambdaQueryWrapper<SocReport>()
                .and(notBlank(request.keyword()), w -> w.like(SocReport::getReportNo, request.keyword()).or().like(SocReport::getTitle, request.keyword()))
                .eq(notBlank(request.reportType()), SocReport::getReportType, request.reportType())
                .orderByDesc(SocReport::getGeneratedAt);
        return PageResult.from(reportMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper));
    }

    @Transactional
    public SocReport generateReport(ReportGenerateRequest request) {
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
        notificationService.dispatch("report_generated", "medium", "report", report.getId(),
                "SOC 报表已生成：" + report.getReportNo(),
                report.getTitle() + " 已生成，可在报表中心导出 PDF 或 Excel。");
        return report;
    }

    public byte[] exportReport(Long id, String format) {
        SocReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("报表不存在");
        }
        if ("pdf".equalsIgnoreCase(format)) {
            return pseudoPdf(report);
        }
        return ExcelExportUtils.export("SOC报表", List.of("模块", "指标", "内容"), reportRows(report));
    }

    public List<SocWazuhConfig> wazuhConfigs() {
        return wazuhConfigMapper.selectList(new LambdaQueryWrapper<SocWazuhConfig>().orderByDesc(SocWazuhConfig::getUpdatedAt));
    }

    public List<SocSyncTask> syncTasks() {
        return syncTaskMapper.selectList(new LambdaQueryWrapper<SocSyncTask>().orderByAsc(SocSyncTask::getId));
    }

    public PageResult<SocVulnerability> vulnerabilities(SocPageRequest request) {
        LambdaQueryWrapper<SocVulnerability> wrapper = new LambdaQueryWrapper<SocVulnerability>()
                .and(notBlank(request.keyword()), w -> w.like(SocVulnerability::getCveId, request.keyword())
                        .or().like(SocVulnerability::getAssetName, request.keyword())
                        .or().like(SocVulnerability::getAssetIp, request.keyword())
                        .or().like(SocVulnerability::getSoftwareName, request.keyword()))
                .eq(notBlank(request.severity()), SocVulnerability::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocVulnerability::getStatus, request.status())
                .orderByDesc(SocVulnerability::getDetectedAt);
        securityScope.applyDataScope(wrapper, SocVulnerability::getOwnerId, SocVulnerability::getDeptId);
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
        LambdaQueryWrapper<SocBaselineCheck> wrapper = new LambdaQueryWrapper<SocBaselineCheck>()
                .and(notBlank(request.keyword()), w -> w.like(SocBaselineCheck::getCheckCode, request.keyword())
                        .or().like(SocBaselineCheck::getCheckItem, request.keyword())
                        .or().like(SocBaselineCheck::getAssetName, request.keyword())
                        .or().like(SocBaselineCheck::getAssetIp, request.keyword()))
                .eq(notBlank(request.category()), SocBaselineCheck::getCategory, request.category())
                .eq(notBlank(request.result()), SocBaselineCheck::getResult, request.result())
                .eq(notBlank(request.status()), SocBaselineCheck::getStatus, request.status())
                .orderByAsc(SocBaselineCheck::getResult)
                .orderByDesc(SocBaselineCheck::getCheckedAt);
        securityScope.applyDataScope(wrapper, SocBaselineCheck::getOwnerId, SocBaselineCheck::getDeptId);
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
        LambdaQueryWrapper<SocFileIntegrityEvent> wrapper = new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .and(notBlank(request.keyword()), w -> w.like(SocFileIntegrityEvent::getEventUid, request.keyword())
                        .or().like(SocFileIntegrityEvent::getHostname, request.keyword())
                        .or().like(SocFileIntegrityEvent::getAssetIp, request.keyword())
                        .or().like(SocFileIntegrityEvent::getFilePath, request.keyword()))
                .eq(notBlank(request.action()), SocFileIntegrityEvent::getAction, request.action())
                .eq(notBlank(request.severity()), SocFileIntegrityEvent::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocFileIntegrityEvent::getStatus, request.status())
                .orderByDesc(SocFileIntegrityEvent::getEventTime);
        securityScope.applyDataScope(wrapper, SocFileIntegrityEvent::getOwnerId, SocFileIntegrityEvent::getDeptId);
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
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .and(notBlank(request.keyword()), w -> w.like(SocExternalEvent::getEventUid, request.keyword())
                        .or().like(SocExternalEvent::getRuleName, request.keyword())
                        .or().like(SocExternalEvent::getAssetName, request.keyword())
                        .or().like(SocExternalEvent::getAssetIp, request.keyword())
                        .or().like(SocExternalEvent::getSrcIp, request.keyword())
                        .or().like(SocExternalEvent::getDestIp, request.keyword())
                        .or().like(SocExternalEvent::getIoc, request.keyword()))
                .eq(notBlank(request.sourceType()), SocExternalEvent::getSourceType, request.sourceType())
                .eq(notBlank(request.eventType()), SocExternalEvent::getEventType, request.eventType())
                .eq(notBlank(request.severity()), SocExternalEvent::getSeverity, request.severity())
                .eq(notBlank(request.status()), SocExternalEvent::getStatus, request.status())
                .orderByDesc(SocExternalEvent::getEventTime);
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
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
        return List.of("suricata", "zeek", "misp", "opencti").stream()
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
        String normalizedEvent = objectMapper.writeValueAsString(Map.of(
                "source", "suricata",
                "event_type", normalizeSuricataEventType(eventType, alertEvent),
                "severity", severity,
                "asset", asset == null ? Objects.toString(assetIp, "") : asset.getHostname(),
                "ioc", Objects.toString(srcIp, ""),
                "rule", ruleName
        ));
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
        alert.setRawRef("suricata/eve.json/" + normalized.eventUid());
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

    private void addImportError(List<String> errors, int lineNumber, String message) {
        if (errors.size() < 5) {
            errors.add("line " + lineNumber + ": " + message);
        }
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
        LambdaQueryWrapper<SocAlert> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocAsset> scopedAssetWrapper() {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocTicket> scopedTicketWrapper() {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocVulnerability> scopedVulnerabilityWrapper() {
        LambdaQueryWrapper<SocVulnerability> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocVulnerability::getOwnerId, SocVulnerability::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocBaselineCheck> scopedBaselineWrapper() {
        LambdaQueryWrapper<SocBaselineCheck> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocBaselineCheck::getOwnerId, SocBaselineCheck::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocFileIntegrityEvent> scopedFileIntegrityWrapper() {
        LambdaQueryWrapper<SocFileIntegrityEvent> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocFileIntegrityEvent::getOwnerId, SocFileIntegrityEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocExternalEvent> scopedExternalEventWrapper() {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocAlertWhitelist> scopedWhitelistWrapper() {
        LambdaQueryWrapper<SocAlertWhitelist> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocAlertWhitelist::getOwnerId, SocAlertWhitelist::getDeptId);
        return wrapper;
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

    private byte[] pseudoPdf(SocReport report) {
        String text = report.getTitle() + "\n\n" + report.getSummary() + "\n\n整改建议\n" + report.getRecommendation();
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        String body = "%%PDF-1.4\n1 0 obj<<>>endobj\n2 0 obj<< /Length %d >>stream\n%s\nendstream\nendobj\ntrailer<<>>\n%%%%EOF";
        return body.formatted(textBytes.length, text).getBytes(StandardCharsets.UTF_8);
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
        long baselinePassRate = baselineChecks == 0 ? 100 : Math.round(((baselineChecks - failedBaselines) * 100.0) / baselineChecks);
        return new ReportMetrics(alerts, critical, high, medium, low, closedTickets, pendingTickets,
                highRiskAssets, allAssets, trend, topAssets, criticalVulnerabilities, highVulnerabilities,
                openVulnerabilities, baselineChecks, failedBaselines, baselinePassRate, fimEvents,
                fimPendingReview, highFimEvents, externalEvents, highExternalEvents, externalSources);
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
                "外部生态：本周期规范化事件 " + metrics.externalEvents() + " 条，高风险 " + metrics.highExternalEvents() + " 条；来源覆盖 " + metrics.externalSources()
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
        suggestions.add("持续复盘误报规则和白名单，降低重复告警噪声。");
        return String.join("；", suggestions);
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

    private List<String> splitReportText(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[；\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
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

    public record TicketDetail(SocTicket ticket, List<SocTicketTimeline> timeline) {
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

    public record SuricataImportResult(int importedEvents, int createdEvents, int updatedEvents, int linkedAlerts,
                                       int skippedLines, List<String> errors) {
    }

    private record UpsertResult(boolean created) {
    }

    private record NormalizedSuricataEvent(String eventUid, String eventType, String severity, String ruleId,
                                           String ruleName, String srcIp, String destIp, String assetName,
                                           String assetIp, String ioc, String rawEvent, String normalizedEvent,
                                           Long ownerId, Long deptId, LocalDateTime eventTime, boolean alertEvent) {
    }

    private record ReportMetrics(long alerts, long critical, long high, long medium, long low, long closedTickets,
                                 long pendingTickets, long highRiskAssets, long allAssets, String trend, String topAssets,
                                 long criticalVulnerabilities, long highVulnerabilities, long openVulnerabilities,
                                 long baselineChecks, long failedBaselines, long baselinePassRate,
                                 long fimEvents, long fimPendingReview, long highFimEvents,
                                 long externalEvents, long highExternalEvents, String externalSources) {
    }
}
