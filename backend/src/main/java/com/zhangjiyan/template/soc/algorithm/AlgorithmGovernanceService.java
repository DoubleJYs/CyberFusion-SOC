package com.zhangjiyan.template.soc.algorithm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.correlation.SocCorrelationRule;
import com.zhangjiyan.template.soc.correlation.SocCorrelationRuleMapper;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.correlation.SocIncidentClusterMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.recommendation.RecommendationService;
import com.zhangjiyan.template.soc.report.SocReport;
import com.zhangjiyan.template.soc.report.SocReportMapper;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshot;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshotMapper;
import com.zhangjiyan.template.soc.risk.SocRiskScoringPolicy;
import com.zhangjiyan.template.soc.risk.SocRiskScoringPolicyMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.trend.TrendAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlgorithmGovernanceService {

    private static final int MAX_REPLAY_SIGNALS = 800;
    private static final int MAX_PREVIEW_ITEMS = 50;
    private static final DateTimeFormatter EVALUATION_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SocCorrelationRuleMapper correlationRuleMapper;
    private final SocRiskScoringPolicyMapper riskPolicyMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocAlertMapper alertMapper;
    private final SocIncidentClusterMapper incidentMapper;
    private final SocAssetMapper assetMapper;
    private final SocAssetRiskSnapshotMapper riskSnapshotMapper;
    private final SocTicketMapper ticketMapper;
    private final SocReportMapper reportMapper;
    private final SocAlgorithmEvaluationMapper evaluationMapper;
    private final SocAlgorithmEvaluationItemMapper evaluationItemMapper;
    private final RecommendationService recommendationService;
    private final TrendAnomalyService trendAnomalyService;
    private final SocSecurityScope securityScope;
    private final ObjectMapper objectMapper;

    public AlgorithmOverview overview() {
        List<PolicyVersionRow> versions = new ArrayList<>();
        versions.addAll(correlationVersions());
        versions.addAll(riskVersions());
        versions.add(builtinVersion("recommendation", "推荐排序规则", "active", 1,
                latestRecommendationRun(), "从事件簇、漏洞、工单、员工任务和体检记录计算推荐优先级。"));
        versions.add(builtinVersion("trend_anomaly", "趋势异常规则", "active", 1,
                latestTrendRun(), "从近 8 天事件和告警检测数量突增、严重占比升高和跨源上升。"));

        List<AlgorithmStatusCard> cards = List.of(
                correlationCard(versions),
                riskCard(versions),
                recommendationCard(versions),
                trendCard(versions)
        );
        List<SocAlgorithmEvaluation> recent = evaluationMapper.selectList(new LambdaQueryWrapper<SocAlgorithmEvaluation>()
                .orderByDesc(SocAlgorithmEvaluation::getCreatedAt)
                .last("LIMIT 8"));
        return new AlgorithmOverview(cards, versions, recent);
    }

    @Transactional
    public AlgorithmReplayResult replay(AlgorithmReplayRequest request) {
        AlgorithmReplayRequest effective = request == null ? new AlgorithmReplayRequest("all", null, null,
                null, null, null, "active", Boolean.FALSE) : request;
        List<ReplaySignal> signals = loadReplaySignals(effective);
        List<PreviewItem> incidentPreview = previewIncidents(signals, effective);
        List<PreviewItem> riskPreview = previewRiskChanges(signals);
        List<PreviewItem> recommendationPreview = previewRecommendations();
        List<PreviewItem> trendPreview = previewTrends();

        int currentIncidentCount = countCurrentIncidents(effective);
        int currentTicketCount = countTickets(effective);
        int currentReportCount = countReports(effective);
        Map<String, Object> diffSummary = new LinkedHashMap<>();
        diffSummary.put("dryRun", true);
        diffSummary.put("realWrites", false);
        diffSummary.put("currentIncidentClusters", currentIncidentCount);
        diffSummary.put("previewIncidentClusters", incidentPreview.size());
        diffSummary.put("previewRiskChanges", riskPreview.size());
        diffSummary.put("previewRecommendations", recommendationPreview.size());
        diffSummary.put("previewTrendAnomalies", trendPreview.size());
        diffSummary.put("currentTickets", currentTicketCount);
        diffSummary.put("currentReports", currentReportCount);
        diffSummary.put("note", "回放只写可选评估记录，不创建真实事件簇、风险快照、工单、报表或通知。");

        List<PreviewItem> items = new ArrayList<>();
        items.addAll(incidentPreview);
        items.addAll(riskPreview);
        items.addAll(recommendationPreview);
        items.addAll(trendPreview);
        items = items.stream().limit(MAX_PREVIEW_ITEMS).toList();

        int outputCount = incidentPreview.size() + riskPreview.size() + recommendationPreview.size() + trendPreview.size();
        String summary = "Dry-run 预览：预计事件簇 %d 个、风险变化 %d 个、推荐动作 %d 条、趋势异常 %d 条；真实业务结果写入 0。"
                .formatted(incidentPreview.size(), riskPreview.size(), recommendationPreview.size(), trendPreview.size());
        SocAlgorithmEvaluation saved = null;
        if (Boolean.TRUE.equals(effective.saveEvaluation())) {
            saved = saveEvaluation(effective, signals.size(), outputCount, diffSummary, summary, items);
        }
        return new AlgorithmReplayResult(true, false, saved == null ? null : saved.getId(),
                saved == null ? null : saved.getEvaluationNo(), signals.size(), outputCount, summary,
                diffSummary, incidentPreview, riskPreview, recommendationPreview, trendPreview, items);
    }

    public PageResult<SocAlgorithmEvaluation> evaluations(long pageNum, long pageSize, String algorithmType, String batchId) {
        LambdaQueryWrapper<SocAlgorithmEvaluation> wrapper = new LambdaQueryWrapper<SocAlgorithmEvaluation>()
                .eq(hasText(algorithmType), SocAlgorithmEvaluation::getAlgorithmType, algorithmType)
                .eq(hasText(batchId), SocAlgorithmEvaluation::getBatchId, batchId)
                .orderByDesc(SocAlgorithmEvaluation::getCreatedAt);
        return PageResult.from(evaluationMapper.selectPage(Page.of(pageNum, pageSize), wrapper));
    }

    public AlgorithmEvaluationDetail evaluationDetail(Long id) {
        SocAlgorithmEvaluation evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) {
            throw new BusinessException("评估记录不存在");
        }
        List<SocAlgorithmEvaluationItem> items = evaluationItemMapper.selectList(new LambdaQueryWrapper<SocAlgorithmEvaluationItem>()
                .eq(SocAlgorithmEvaluationItem::getEvaluationId, id)
                .orderByAsc(SocAlgorithmEvaluationItem::getSortOrder)
                .orderByAsc(SocAlgorithmEvaluationItem::getId));
        return new AlgorithmEvaluationDetail(evaluation, items);
    }

    private List<PolicyVersionRow> correlationVersions() {
        return correlationRuleMapper.selectList(new LambdaQueryWrapper<SocCorrelationRule>()
                        .eq(SocCorrelationRule::getDeleted, 0)
                        .orderByDesc(SocCorrelationRule::getUpdatedAt)
                        .last("LIMIT 80"))
                .stream()
                .map(rule -> new PolicyVersionRow("correlation", rule.getId(), firstNotBlank(rule.getRuleCode(), rule.getRuleKey()),
                        rule.getRuleName(), rule.getStatus(), truthy(rule.getEnabled()), nz(rule.getVersion()), rule.getUpdatedBy(),
                        rule.getUpdatedAt(), firstNotBlank(rule.getSourceTypesJson(), "[]"), rule.getDescription()))
                .toList();
    }

    private List<PolicyVersionRow> riskVersions() {
        return riskPolicyMapper.selectList(new LambdaQueryWrapper<SocRiskScoringPolicy>()
                        .eq(SocRiskScoringPolicy::getDeleted, 0)
                        .orderByDesc(SocRiskScoringPolicy::getUpdatedAt)
                        .last("LIMIT 80"))
                .stream()
                .map(policy -> new PolicyVersionRow("risk_scoring", policy.getId(), policy.getPolicyCode(),
                        policy.getPolicyName(), policy.getStatus(), Objects.equals(policy.getEnabled(), 1), nz(policy.getVersion()),
                        policy.getUpdatedBy(), policy.getUpdatedAt(), "asset,alert,vulnerability,baseline,fim,ticket,checkup",
                        policy.getDescription()))
                .toList();
    }

    private AlgorithmStatusCard correlationCard(List<PolicyVersionRow> versions) {
        List<PolicyVersionRow> rows = versions.stream().filter(row -> "correlation".equals(row.algorithmType())).toList();
        return card("correlation", "事件关联规则", rows, latestIncidentRun(),
                countRecentIncidents(), countAlertsByStatus("false_positive"), countAlertsByStatus("ignored"),
                countClosedIncidents(), sourceCoverage(), "把多源证据解释性聚合为安全事件簇。");
    }

    private AlgorithmStatusCard riskCard(List<PolicyVersionRow> versions) {
        List<PolicyVersionRow> rows = versions.stream().filter(row -> "risk_scoring".equals(row.algorithmType())).toList();
        return card("risk_scoring", "风险评分策略", rows, latestRiskRun(),
                countRecentRiskSnapshots(), countAlertsByStatus("false_positive"), countAlertsByStatus("ignored"),
                countAlertsByStatus("closed"), sourceCoverage(), "把资产、告警、漏洞、基线、FIM、工单和员工任务聚合为可解释风险分。");
    }

    private AlgorithmStatusCard recommendationCard(List<PolicyVersionRow> versions) {
        List<PolicyVersionRow> rows = versions.stream().filter(row -> "recommendation".equals(row.algorithmType())).toList();
        int hitCount = safeList(() -> recommendationService.topRecommendations(20)).size();
        return card("recommendation", "推荐排序规则", rows, latestRecommendationRun(),
                hitCount, countAlertsByStatus("false_positive"), countAlertsByStatus("ignored"),
                countAlertsByStatus("closed"), sourceCoverage(), "把高危漏洞、事件簇、超时工单和员工待办转成处置建议排序。");
    }

    private AlgorithmStatusCard trendCard(List<PolicyVersionRow> versions) {
        List<PolicyVersionRow> rows = versions.stream().filter(row -> "trend_anomaly".equals(row.algorithmType())).toList();
        int hitCount = safeList(() -> trendAnomalyService.topAnomalies(20)).size();
        return card("trend_anomaly", "趋势异常规则", rows, latestTrendRun(),
                hitCount, countAlertsByStatus("false_positive"), countAlertsByStatus("ignored"),
                countAlertsByStatus("closed"), sourceCoverage(), "按数量突增、严重级别占比和跨源上升识别趋势异常。");
    }

    private AlgorithmStatusCard card(String type, String name, List<PolicyVersionRow> rows, LocalDateTime lastRunAt,
                                     int recentHitCount, long falsePositiveCount, long ignoredCount, long closedCount,
                                     List<String> sourceCoverage, String summary) {
        PolicyVersionRow latest = rows.stream().max(Comparator.comparing(PolicyVersionRow::updatedAt,
                Comparator.nullsFirst(LocalDateTime::compareTo))).orElse(null);
        return new AlgorithmStatusCard(type, name,
                (int) rows.stream().filter(row -> "active".equalsIgnoreCase(row.status()) && row.enabled()).count(),
                (int) rows.stream().filter(row -> "draft".equalsIgnoreCase(row.status())).count(),
                (int) rows.stream().filter(row -> "disabled".equalsIgnoreCase(row.status()) || !row.enabled()).count(),
                lastRunAt, recentHitCount, falsePositiveCount, ignoredCount, closedCount,
                sourceCoverage, latest == null ? null : latest.updatedBy(), latest == null ? null : latest.version(), summary);
    }

    private List<ReplaySignal> loadReplaySignals(AlgorithmReplayRequest request) {
        List<ReplaySignal> result = new ArrayList<>();
        LocalDateTime fallbackStart = LocalDateTime.now().minusDays(7);

        LambdaQueryWrapper<SocExternalEvent> eventWrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .eq(hasText(request.batchId()), SocExternalEvent::getBatchId, request.batchId())
                .ge(request.timeRangeStart() != null, SocExternalEvent::getEventTime, request.timeRangeStart())
                .le(request.timeRangeEnd() != null, SocExternalEvent::getEventTime, request.timeRangeEnd())
                .ge(!hasText(request.batchId()) && request.timeRangeStart() == null, SocExternalEvent::getEventTime, fallbackStart)
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT " + MAX_REPLAY_SIGNALS);
        securityScope.applyDataScope(eventWrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        for (SocExternalEvent event : externalEventMapper.selectList(eventWrapper)) {
            result.add(new ReplaySignal("external_event", event.getId(), event.getSourceType(), event.getEventType(),
                    event.getSeverity(), event.getRuleId(), firstNotBlank(event.getAssetIp(), event.getDestIp(), event.getSrcIp()),
                    event.getAssetName(), event.getBatchId(), event.getDemoCaseId(), event.getTargetUrl(), event.getEventTime()));
        }

        LambdaQueryWrapper<SocAlert> alertWrapper = new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getDeleted, 0)
                .eq(hasText(request.batchId()), SocAlert::getBatchId, request.batchId())
                .ge(request.timeRangeStart() != null, SocAlert::getEventTime, request.timeRangeStart())
                .le(request.timeRangeEnd() != null, SocAlert::getEventTime, request.timeRangeEnd())
                .ge(!hasText(request.batchId()) && request.timeRangeStart() == null, SocAlert::getEventTime, fallbackStart)
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT " + MAX_REPLAY_SIGNALS);
        securityScope.applyDataScope(alertWrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
        for (SocAlert alert : alertMapper.selectList(alertWrapper)) {
            result.add(new ReplaySignal("alert", alert.getId(), alert.getSourceType(), alert.getEventType(),
                    alert.getSeverity(), alert.getRuleId(), alert.getAssetIp(), alert.getAssetName(), alert.getBatchId(),
                    alert.getDemoCaseId(), alert.getTargetUrl(), alert.getEventTime()));
        }
        return result.stream()
                .sorted(Comparator.comparing(ReplaySignal::eventTime, Comparator.nullsLast(LocalDateTime::compareTo)))
                .limit(MAX_REPLAY_SIGNALS)
                .toList();
    }

    private List<PreviewItem> previewIncidents(List<ReplaySignal> signals, AlgorithmReplayRequest request) {
        Map<String, List<ReplaySignal>> grouped = signals.stream()
                .filter(signal -> hasText(signal.assetIp()))
                .collect(Collectors.groupingBy(signal -> String.join("|", safe(signal.assetIp()), safe(signal.batchId()),
                        safe(signal.demoCaseId())), LinkedHashMap::new, Collectors.toList()));
        List<PreviewItem> result = new ArrayList<>();
        grouped.forEach((key, group) -> {
            Set<String> sources = group.stream().map(ReplaySignal::sourceType).filter(AlgorithmGovernanceService::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (group.size() < 2 && sources.size() < 2) {
                return;
            }
            ReplaySignal first = group.get(0);
            ReplaySignal last = group.get(group.size() - 1);
            int highCount = (int) group.stream().filter(signal -> Set.of("high", "critical", "严重", "高危").contains(safe(signal.severity()).toLowerCase(Locale.ROOT))).count();
            int score = Math.min(100, 35 + sources.size() * 15 + group.size() * 3 + highCount * 8);
            String reason = "同一资产 %s 在回放窗口内出现 %s 证据，共 %d 条，batchId=%s、demoCaseId=%s，因此 dry-run 会关联为事件簇。"
                    .formatted(first.assetIp(), sources.isEmpty() ? "多源" : String.join("+", sources), group.size(),
                            display(first.batchId()), display(first.demoCaseId()));
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("assetIp", first.assetIp());
            preview.put("hostname", first.hostname());
            preview.put("batchId", first.batchId());
            preview.put("demoCaseId", first.demoCaseId());
            preview.put("sourceTypes", sources);
            preview.put("evidenceCount", group.size());
            preview.put("score", score);
            preview.put("firstSeenAt", first.eventTime());
            preview.put("lastSeenAt", last.eventTime());
            preview.put("policyMode", firstNotBlank(request.policyMode(), "active"));
            result.add(new PreviewItem("incident_cluster", "预计事件簇：" + first.assetIp(), reason, preview));
        });
        return result.stream()
                .sorted(Comparator.comparing(item -> -number(item.previewResult().get("score"))))
                .limit(12)
                .toList();
    }

    private List<PreviewItem> previewRiskChanges(List<ReplaySignal> signals) {
        Map<String, List<ReplaySignal>> byAsset = signals.stream()
                .filter(signal -> hasText(signal.assetIp()))
                .collect(Collectors.groupingBy(ReplaySignal::assetIp, LinkedHashMap::new, Collectors.toList()));
        List<PreviewItem> result = new ArrayList<>();
        byAsset.forEach((assetIp, group) -> {
            int highCount = (int) group.stream().filter(signal -> Set.of("high", "critical", "严重", "高危").contains(safe(signal.severity()).toLowerCase(Locale.ROOT))).count();
            int crossSource = (int) group.stream().map(ReplaySignal::sourceType).filter(AlgorithmGovernanceService::hasText).distinct().count();
            int delta = Math.min(35, highCount * 7 + Math.max(0, crossSource - 1) * 5 + Math.min(group.size(), 8));
            if (delta <= 0) {
                return;
            }
            SocAsset asset = assetByIp(assetIp);
            SocAssetRiskSnapshot snapshot = latestSnapshot(asset == null ? null : asset.getId(), assetIp);
            int currentScore = snapshot == null ? (asset == null || asset.getRiskScore() == null ? 0 : asset.getRiskScore()) : snapshot.getScore();
            int previewScore = Math.min(100, currentScore + delta);
            String reason = "该资产在回放窗口内存在 %d 条高危/严重信号并覆盖 %d 类来源，因此 dry-run 风险分从 %d 预估调整到 %d。"
                    .formatted(highCount, crossSource, currentScore, previewScore);
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("assetId", asset == null ? null : asset.getId());
            preview.put("assetIp", assetIp);
            preview.put("hostname", asset == null ? null : asset.getHostname());
            preview.put("currentScore", currentScore);
            preview.put("previewScore", previewScore);
            preview.put("delta", previewScore - currentScore);
            preview.put("signalCount", group.size());
            result.add(new PreviewItem("risk_change", "预计风险变化：" + assetIp, reason, preview));
        });
        return result.stream()
                .sorted(Comparator.comparing(item -> -number(item.previewResult().get("delta"))))
                .limit(10)
                .toList();
    }

    private List<PreviewItem> previewRecommendations() {
        return safeList(() -> recommendationService.topRecommendations(8)).stream()
                .map(item -> {
                    Map<String, Object> preview = new LinkedHashMap<>();
                    preview.put("key", item.key());
                    preview.put("priority", item.priority());
                    preview.put("priorityScore", item.priorityScore());
                    preview.put("recommendedAction", item.recommendedAction());
                    preview.put("relatedBizType", item.relatedBizType());
                    preview.put("relatedBizId", item.relatedBizId());
                    preview.put("assetIp", item.assetIp());
                    String reason = firstNotBlank(item.reason(), "该推荐根据事件簇、漏洞、工单或员工任务优先级排序生成。");
                    return new PreviewItem("recommendation", item.title(), reason, preview);
                })
                .limit(8)
                .toList();
    }

    private List<PreviewItem> previewTrends() {
        return safeList(() -> trendAnomalyService.topAnomalies(8)).stream()
                .map(item -> {
                    Map<String, Object> preview = new LinkedHashMap<>();
                    preview.put("assetIp", item.assetIp());
                    preview.put("sourceType", item.sourceType());
                    preview.put("eventType", item.eventType());
                    preview.put("currentCount", item.currentCount());
                    preview.put("baselineCount", item.baselineCount());
                    preview.put("changeRatio", item.changeRatio());
                    preview.put("anomalyScore", item.anomalyScore());
                    return new PreviewItem("trend_anomaly", item.title(), item.reason(), preview);
                })
                .limit(8)
                .toList();
    }

    private SocAlgorithmEvaluation saveEvaluation(AlgorithmReplayRequest request, int inputCount, int outputCount,
                                                  Map<String, Object> diffSummary, String summary, List<PreviewItem> items) {
        SocAlgorithmEvaluation evaluation = new SocAlgorithmEvaluation();
        evaluation.setEvaluationNo("ALGO-" + LocalDateTime.now().format(EVALUATION_NO_TIME) + "-" + UUID.randomUUID().toString().substring(0, 8));
        evaluation.setAlgorithmType(firstNotBlank(request.algorithmType(), "all"));
        evaluation.setPolicyId(request.policyId());
        evaluation.setPolicyVersion(request.policyVersion());
        evaluation.setBatchId(request.batchId());
        evaluation.setTimeRangeStart(request.timeRangeStart());
        evaluation.setTimeRangeEnd(request.timeRangeEnd());
        evaluation.setInputCount(inputCount);
        evaluation.setOutputCount(outputCount);
        evaluation.setDiffSummaryJson(toJson(diffSummary));
        evaluation.setResultSummary(summary);
        evaluation.setCreatedBy(securityScope.currentUserId());
        evaluationMapper.insert(evaluation);

        int sort = 1;
        for (PreviewItem item : items) {
            SocAlgorithmEvaluationItem row = new SocAlgorithmEvaluationItem();
            row.setEvaluationId(evaluation.getId());
            row.setItemType(item.itemType());
            row.setItemName(limit(item.title(), 255));
            row.setPreviewResultJson(toJson(item.previewResult()));
            row.setReason(limit(item.reason(), 1000));
            row.setSortOrder(sort++);
            evaluationItemMapper.insert(row);
        }
        return evaluation;
    }

    private int countCurrentIncidents(AlgorithmReplayRequest request) {
        LambdaQueryWrapper<SocIncidentCluster> wrapper = new LambdaQueryWrapper<SocIncidentCluster>()
                .eq(SocIncidentCluster::getDeleted, 0)
                .eq(hasText(request.batchId()), SocIncidentCluster::getBatchId, request.batchId())
                .ge(request.timeRangeStart() != null, SocIncidentCluster::getLastSeenAt, request.timeRangeStart())
                .le(request.timeRangeEnd() != null, SocIncidentCluster::getFirstSeenAt, request.timeRangeEnd());
        securityScope.applyDataScope(wrapper, SocIncidentCluster::getOwnerId, SocIncidentCluster::getDeptId);
        return incidentMapper.selectCount(wrapper).intValue();
    }

    private int countTickets(AlgorithmReplayRequest request) {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<SocTicket>().eq(SocTicket::getDeleted, 0);
        if (hasText(request.batchId())) {
            wrapper.like(SocTicket::getTitle, request.batchId());
        }
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
        return ticketMapper.selectCount(wrapper).intValue();
    }

    private int countReports(AlgorithmReplayRequest request) {
        LambdaQueryWrapper<SocReport> wrapper = new LambdaQueryWrapper<SocReport>().eq(SocReport::getDeleted, 0);
        if (hasText(request.batchId())) {
            wrapper.and(w -> w.like(SocReport::getTitle, request.batchId()).or().like(SocReport::getSummary, request.batchId()));
        }
        return reportMapper.selectCount(wrapper).intValue();
    }

    private long countAlertsByStatus(String status) {
        return alertMapper.selectCount(new LambdaQueryWrapper<SocAlert>().eq(SocAlert::getDeleted, 0).eq(SocAlert::getStatus, status));
    }

    private long countClosedIncidents() {
        return incidentMapper.selectCount(new LambdaQueryWrapper<SocIncidentCluster>().eq(SocIncidentCluster::getDeleted, 0).eq(SocIncidentCluster::getStatus, "closed"));
    }

    private int countRecentIncidents() {
        return incidentMapper.selectCount(new LambdaQueryWrapper<SocIncidentCluster>()
                .eq(SocIncidentCluster::getDeleted, 0)
                .ge(SocIncidentCluster::getUpdatedAt, LocalDateTime.now().minusDays(7))).intValue();
    }

    private int countRecentRiskSnapshots() {
        return riskSnapshotMapper.selectCount(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .ge(SocAssetRiskSnapshot::getCalculatedAt, LocalDateTime.now().minusDays(7))).intValue();
    }

    private LocalDateTime latestIncidentRun() {
        SocIncidentCluster latest = incidentMapper.selectOne(new LambdaQueryWrapper<SocIncidentCluster>()
                .eq(SocIncidentCluster::getDeleted, 0)
                .orderByDesc(SocIncidentCluster::getUpdatedAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getUpdatedAt();
    }

    private LocalDateTime latestRiskRun() {
        SocAssetRiskSnapshot latest = riskSnapshotMapper.selectOne(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getCalculatedAt();
    }

    private LocalDateTime latestRecommendationRun() {
        return latestIncidentRun();
    }

    private LocalDateTime latestTrendRun() {
        SocExternalEvent latest = externalEventMapper.selectOne(new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getEventTime();
    }

    private List<String> sourceCoverage() {
        Set<String> sources = new LinkedHashSet<>();
        externalEventMapper.selectList(new LambdaQueryWrapper<SocExternalEvent>()
                        .eq(SocExternalEvent::getDeleted, 0)
                        .isNotNull(SocExternalEvent::getSourceType)
                        .orderByDesc(SocExternalEvent::getEventTime)
                        .last("LIMIT 400"))
                .forEach(event -> addIfText(sources, event.getSourceType()));
        alertMapper.selectList(new LambdaQueryWrapper<SocAlert>()
                        .eq(SocAlert::getDeleted, 0)
                        .isNotNull(SocAlert::getSourceType)
                        .orderByDesc(SocAlert::getEventTime)
                        .last("LIMIT 200"))
                .forEach(alert -> addIfText(sources, alert.getSourceType()));
        return sources.stream().limit(12).toList();
    }

    private SocAsset assetByIp(String assetIp) {
        if (!hasText(assetIp)) return null;
        return assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getDeleted, 0)
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
    }

    private SocAssetRiskSnapshot latestSnapshot(Long assetId, String assetIp) {
        LambdaQueryWrapper<SocAssetRiskSnapshot> wrapper = new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 1");
        if (assetId != null) {
            wrapper.eq(SocAssetRiskSnapshot::getAssetId, assetId);
        } else if (hasText(assetIp)) {
            wrapper.eq(SocAssetRiskSnapshot::getAssetIp, assetIp);
        } else {
            return null;
        }
        return riskSnapshotMapper.selectOne(wrapper);
    }

    private PolicyVersionRow builtinVersion(String type, String name, String status, int version,
                                            LocalDateTime updatedAt, String description) {
        return new PolicyVersionRow(type, null, type + "_builtin_v" + version, name, status, true, version,
                null, updatedAt, "events,alerts,incidents,vulnerabilities,tickets,tasks", description);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static boolean truthy(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String display(String value) {
        return hasText(value) ? value.trim() : "-";
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private static String limit(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return value.substring(0, limit);
    }

    private static void addIfText(Set<String> values, String value) {
        if (hasText(value)) {
            values.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static <T> List<T> safeList(SupplierWithException<List<T>> supplier) {
        try {
            List<T> value = supplier.get();
            return value == null ? List.of() : value;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }

    private record ReplaySignal(String recordType, Long id, String sourceType, String eventType, String severity,
                                String ruleId, String assetIp, String hostname, String batchId, String demoCaseId,
                                String targetUrl, LocalDateTime eventTime) {
    }

    public record AlgorithmOverview(List<AlgorithmStatusCard> cards, List<PolicyVersionRow> policyVersions,
                                    List<SocAlgorithmEvaluation> recentEvaluations) {
    }

    public record AlgorithmStatusCard(String algorithmType, String displayName, int activePolicyCount,
                                      int draftPolicyCount, int disabledPolicyCount, LocalDateTime lastRunAt,
                                      int recentHitCount, long falsePositiveCount, long ignoredCount,
                                      long closedCount, List<String> sourceCoverage, Long updatedBy,
                                      Integer version, String summary) {
    }

    public record PolicyVersionRow(String algorithmType, Long policyId, String policyCode, String policyName,
                                   String status, boolean enabled, int version, Long updatedBy,
                                   LocalDateTime updatedAt, String sourceCoverage, String description) {
    }

    public record AlgorithmReplayRequest(String algorithmType, Long policyId, Integer policyVersion, String batchId,
                                         LocalDateTime timeRangeStart, LocalDateTime timeRangeEnd,
                                         String policyMode, Boolean saveEvaluation) {
    }

    public record PreviewItem(String itemType, String title, String reason, Map<String, Object> previewResult) {
    }

    public record AlgorithmReplayResult(boolean dryRun, boolean realWrites, Long evaluationId, String evaluationNo,
                                        int inputCount, int outputCount, String resultSummary,
                                        Map<String, Object> diffSummary, List<PreviewItem> incidentPreview,
                                        List<PreviewItem> riskPreview, List<PreviewItem> recommendationPreview,
                                        List<PreviewItem> trendPreview, List<PreviewItem> allItems) {
    }

    public record AlgorithmEvaluationDetail(SocAlgorithmEvaluation evaluation, List<SocAlgorithmEvaluationItem> items) {
    }
}
