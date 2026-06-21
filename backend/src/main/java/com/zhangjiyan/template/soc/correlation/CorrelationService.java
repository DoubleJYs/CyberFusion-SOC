package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.ticket.SocTicketTimeline;
import com.zhangjiyan.template.soc.ticket.SocTicketTimelineMapper;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CorrelationService {

    private static final Set<String> RULE_TYPES = Set.of("event_count", "value_count", "frequency", "temporal", "temporal_ordered", "cross_source_chain");
    private static final Set<String> SEVERITIES = Set.of("critical", "high", "medium", "low", "info");
    private static final Set<String> FORBIDDEN_CONFIG_TOKENS = Set.of("script", "eval", "shell", "curl", "wget", "http://", "https://", "python", "bash", "powershell");

    private final SocCorrelationRuleMapper ruleMapper;
    private final SocIncidentClusterMapper clusterMapper;
    private final SocIncidentEvidenceMapper evidenceMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocAlertMapper alertMapper;
    private final SocVulnerabilityMapper vulnerabilityMapper;
    private final SocAssetMapper assetMapper;
    private final SocTicketMapper ticketMapper;
    private final SocTicketTimelineMapper timelineMapper;
    private final SocSecurityScope securityScope;
    private final ObjectMapper objectMapper;

    public PageResult<SocIncidentCluster> incidents(long pageNum, long pageSize, String status, String severity, String keyword) {
        LambdaQueryWrapper<SocIncidentCluster> wrapper = scopedClusterWrapper()
                .eq(notBlank(status), SocIncidentCluster::getStatus, status)
                .eq(notBlank(severity), SocIncidentCluster::getSeverity, severity)
                .and(notBlank(keyword), w -> w.like(SocIncidentCluster::getClusterNo, keyword)
                        .or().like(SocIncidentCluster::getTitle, keyword)
                        .or().like(SocIncidentCluster::getPrimaryAssetIp, keyword)
                        .or().like(SocIncidentCluster::getBatchId, keyword)
                        .or().like(SocIncidentCluster::getDemoCaseId, keyword)
                        .or().like(SocIncidentCluster::getCorrelationKey, keyword))
                .orderByDesc(SocIncidentCluster::getLastSeenAt)
                .orderByDesc(SocIncidentCluster::getUpdatedAt);
        return PageResult.from(clusterMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public SocIncidentCluster detail(Long id) {
        SocIncidentCluster cluster = clusterMapper.selectById(id);
        if (cluster == null || Integer.valueOf(1).equals(cluster.getDeleted())) {
            throw new BusinessException("事件簇不存在");
        }
        ensureAccess(cluster.getOwnerId(), cluster.getDeptId(), "无权访问该事件簇");
        cluster.setEvidence(evidenceMapper.selectList(new LambdaQueryWrapper<SocIncidentEvidence>()
                .eq(SocIncidentEvidence::getClusterId, id)
                .orderByAsc(SocIncidentEvidence::getEventTime)));
        return cluster;
    }

    @Transactional
    public CorrelateResult correlate() {
        List<SocCorrelationRule> rules = activeRules();
        List<EvidenceCandidate> candidates = collectCandidates();
        int before = clusterMapper.selectCount(scopedClusterWrapper()).intValue();
        int upserted = 0;
        int evidenceWritten = 0;
        for (SocCorrelationRule rule : rules) {
            Map<String, List<EvidenceCandidate>> groups = groupCandidates(candidates, rule);
            for (Map.Entry<String, List<EvidenceCandidate>> entry : groups.entrySet()) {
                List<EvidenceCandidate> group = eligibleGroup(rule, entry.getValue());
                if (group.isEmpty()) {
                    continue;
                }
                SocIncidentCluster cluster = upsertCluster(rule, entry.getKey(), group);
                evidenceWritten += rewriteEvidence(cluster, group);
                upserted++;
            }
        }
        int after = clusterMapper.selectCount(scopedClusterWrapper()).intValue();
        return new CorrelateResult(upserted, Math.max(0, after - before), evidenceWritten, rules.size());
    }

    @Transactional
    public SocTicket createTicket(Long clusterId, IncidentActionRequest request) {
        SocIncidentCluster cluster = detail(clusterId);
        if (cluster.getTicketId() != null) {
            SocTicket existing = ticketMapper.selectById(cluster.getTicketId());
            if (existing != null) {
                return existing;
            }
        }
        SocIncidentEvidence firstAlert = cluster.getEvidence() == null ? null : cluster.getEvidence().stream()
                .filter(e -> "alert".equals(e.getEvidenceType()))
                .findFirst()
                .orElse(null);
        SocTicket ticket = new SocTicket();
        ticket.setTicketNo("INC-CLUSTER-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + cluster.getId());
        ticket.setAlertId(firstAlert == null ? null : firstAlert.getEvidenceId());
        ticket.setTitle("安全事件簇处置：" + cluster.getTitle());
        ticket.setSeverity(cluster.getSeverity());
        ticket.setStatus("待分派");
        ticket.setAssigneeId(request == null ? null : request.assigneeId());
        ticket.setDeptId(cluster.getDeptId());
        ticket.setDueAt(LocalDateTime.now().plusHours("critical".equals(cluster.getSeverity()) ? 4 : 24));
        ticketMapper.insert(ticket);
        appendTimeline(ticket.getId(), "事件簇转工单", null, "待分派",
                firstNotBlank(request == null ? null : request.remark(), "由事件关联引擎生成处置工单") + "；事件簇：" + cluster.getClusterNo());
        cluster.setTicketId(ticket.getId());
        cluster.setStatus("ticketed");
        clusterMapper.updateById(cluster);
        if (firstAlert != null) {
            SocAlert alert = alertMapper.selectById(firstAlert.getEvidenceId());
            if (alert != null) {
                alert.setTicketId(ticket.getId());
                alert.setStatus("ticketed");
                alertMapper.updateById(alert);
            }
        }
        return ticket;
    }

    @Transactional
    public SocIncidentCluster close(Long id, IncidentActionRequest request) {
        SocIncidentCluster cluster = detail(id);
        cluster.setStatus("closed");
        cluster.setClosedAt(LocalDateTime.now());
        clusterMapper.updateById(cluster);
        if (cluster.getTicketId() != null) {
            appendTimeline(cluster.getTicketId(), "关闭事件簇", null, "closed",
                    firstNotBlank(request == null ? null : request.remark(), "事件簇已关闭"));
        }
        return detail(id);
    }

    public List<SocIncidentCluster> relatedIncidentsForAlert(Long alertId) {
        List<Long> clusterIds = evidenceMapper.selectList(new LambdaQueryWrapper<SocIncidentEvidence>()
                        .eq(SocIncidentEvidence::getEvidenceType, "alert")
                        .eq(SocIncidentEvidence::getEvidenceId, alertId))
                .stream().map(SocIncidentEvidence::getClusterId).distinct().toList();
        if (clusterIds.isEmpty()) {
            return List.of();
        }
        return clusterMapper.selectList(scopedClusterWrapper().in(SocIncidentCluster::getId, clusterIds)
                .orderByDesc(SocIncidentCluster::getLastSeenAt));
    }

    public List<SocIncidentCluster> incidentsForAsset(Long assetId) {
        SocAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        ensureAccess(asset.getOwnerId(), asset.getDeptId(), "无权访问该资产事件簇");
        return clusterMapper.selectList(scopedClusterWrapper()
                .eq(SocIncidentCluster::getPrimaryAssetIp, asset.getIp())
                .orderByDesc(SocIncidentCluster::getLastSeenAt));
    }

    public PageResult<SocCorrelationRule> rules(long pageNum, long pageSize, String status, String type, String keyword) {
        LambdaQueryWrapper<SocCorrelationRule> wrapper = new LambdaQueryWrapper<SocCorrelationRule>()
                .eq(SocCorrelationRule::getDeleted, 0)
                .eq(notBlank(status), SocCorrelationRule::getStatus, status)
                .eq(notBlank(type), SocCorrelationRule::getRuleType, type)
                .and(notBlank(keyword), w -> w.like(SocCorrelationRule::getRuleKey, keyword)
                        .or().like(SocCorrelationRule::getRuleCode, keyword)
                        .or().like(SocCorrelationRule::getRuleName, keyword))
                .orderByDesc(SocCorrelationRule::getUpdatedAt);
        return PageResult.from(ruleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public SocCorrelationRule ruleDetail(Long id) {
        SocCorrelationRule rule = ruleMapper.selectById(id);
        if (rule == null || Integer.valueOf(1).equals(rule.getDeleted())) {
            throw new BusinessException("关联规则不存在");
        }
        return rule;
    }

    @Transactional
    public SocCorrelationRule createRule(CorrelationRuleRequest request) {
        validateRuleRequest(request);
        SocCorrelationRule rule = new SocCorrelationRule();
        applyRule(rule, request);
        rule.setStatus(firstNotBlank(request.status(), "draft"));
        rule.setCreatedBy(securityScope.currentUserId());
        rule.setUpdatedBy(securityScope.currentUserId());
        ruleMapper.insert(rule);
        return rule;
    }

    @Transactional
    public SocCorrelationRule updateRule(Long id, CorrelationRuleRequest request) {
        validateRuleRequest(request);
        SocCorrelationRule rule = ruleDetail(id);
        applyRule(rule, request);
        rule.setUpdatedBy(securityScope.currentUserId());
        ruleMapper.updateById(rule);
        return ruleDetail(id);
    }

    public ValidationResult validateRule(Long id) {
        SocCorrelationRule rule = ruleDetail(id);
        validateRuleShape(rule.getRuleType(), rule.getSourceTypesJson(), rule.getEventTypesJson(),
                firstNotBlank(rule.getGroupByFieldsJson(), rule.getGroupByJson()), rule.getSequenceJson(),
                threshold(rule), timeframeSeconds(rule), severityFloor(rule));
        return new ValidationResult(true, "关联规则配置安全校验通过，仅包含结构化字段映射和时间窗口。");
    }

    @Transactional
    public SocCorrelationRule publishRule(Long id) {
        SocCorrelationRule rule = ruleDetail(id);
        validateRule(id);
        rule.setStatus("active");
        rule.setEnabled(true);
        rule.setApprovedBy(securityScope.currentUserId());
        rule.setApprovedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return ruleDetail(id);
    }

    @Transactional
    public SocCorrelationRule disableRule(Long id) {
        SocCorrelationRule rule = ruleDetail(id);
        rule.setStatus("disabled");
        rule.setEnabled(false);
        rule.setUpdatedBy(securityScope.currentUserId());
        ruleMapper.updateById(rule);
        return ruleDetail(id);
    }

    private List<SocCorrelationRule> activeRules() {
        List<SocCorrelationRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<SocCorrelationRule>()
                .eq(SocCorrelationRule::getDeleted, 0)
                .eq(SocCorrelationRule::getStatus, "active")
                .eq(SocCorrelationRule::getEnabled, true)
                .orderByAsc(SocCorrelationRule::getId));
        if (!rules.isEmpty()) {
            return rules;
        }
        SocCorrelationRule fallback = new SocCorrelationRule();
        fallback.setId(-1L);
        fallback.setRuleCode("builtin_cross_source_chain");
        fallback.setRuleKey("builtin_cross_source_chain");
        fallback.setRuleName("内置同资产多源链路");
        fallback.setRuleType("cross_source_chain");
        fallback.setMinCount(2);
        fallback.setThreshold(2);
        fallback.setTimeWindowMinutes(30);
        fallback.setTimeframeSeconds(1800);
        fallback.setSeverityMin("low");
        fallback.setSeverityFloor("low");
        fallback.setGroupByFieldsJson("[\"assetIp\",\"batchId\",\"demoCaseId\"]");
        fallback.setGroupByJson("[\"assetIp\",\"batchId\",\"demoCaseId\"]");
        fallback.setSourceTypesJson("[\"waf\",\"zap\",\"wazuh\",\"suricata\",\"zeek\",\"trivy\"]");
        return List.of(fallback);
    }

    private List<EvidenceCandidate> collectCandidates() {
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        List<EvidenceCandidate> result = new ArrayList<>();
        externalEventMapper.selectList(scopedExternalEventWrapper()
                        .ge(SocExternalEvent::getEventTime, since)
                        .orderByDesc(SocExternalEvent::getEventTime)
                        .last("LIMIT 1000"))
                .forEach(event -> result.add(fromExternalEvent(event)));
        alertMapper.selectList(scopedAlertWrapper()
                        .ge(SocAlert::getEventTime, since)
                        .orderByDesc(SocAlert::getEventTime)
                        .last("LIMIT 1000"))
                .forEach(alert -> result.add(fromAlert(alert)));
        vulnerabilityMapper.selectList(scopedVulnerabilityWrapper()
                        .ge(SocVulnerability::getDetectedAt, since.minusDays(14))
                        .orderByDesc(SocVulnerability::getDetectedAt)
                        .last("LIMIT 500"))
                .forEach(vulnerability -> result.add(fromVulnerability(vulnerability)));
        return result;
    }

    private Map<String, List<EvidenceCandidate>> groupCandidates(List<EvidenceCandidate> candidates, SocCorrelationRule rule) {
        Set<String> allowedSources = jsonSet(rule.getSourceTypesJson());
        Set<String> allowedEventTypes = jsonSet(rule.getEventTypesJson());
        int timeframe = timeframeSeconds(rule);
        return candidates.stream()
                .filter(candidate -> allowedSources.isEmpty() || allowedSources.contains(candidate.sourceType()))
                .filter(candidate -> allowedEventTypes.isEmpty() || allowedEventTypes.contains(candidate.eventType()))
                .filter(candidate -> meetsSeverityFloor(candidate, severityFloor(rule)))
                .collect(Collectors.groupingBy(candidate -> groupKey(candidate, rule, timeframe)));
    }

    private List<EvidenceCandidate> eligibleGroup(SocCorrelationRule rule, List<EvidenceCandidate> candidates) {
        int threshold = threshold(rule);
        List<EvidenceCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(EvidenceCandidate::eventTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        long structuredEvents = sorted.stream().filter(item -> !"vulnerability".equals(item.evidenceType())).count();
        String ruleType = rule.getRuleType();
        if ("value_count".equals(ruleType)) {
            long distinctValues = sorted.stream()
                    .filter(item -> !"vulnerability".equals(item.evidenceType()))
                    .map(this::distinctValueKey)
                    .filter(CorrelationService::notBlank)
                    .distinct()
                    .count();
            return eligibleByScore(rule, distinctValues >= threshold ? sorted : List.of());
        }
        if ("temporal".equals(ruleType)) {
            List<String> sequence = jsonList(rule.getSequenceJson());
            boolean matched = sequence.isEmpty()
                    ? structuredEvents >= threshold
                    : sequence.stream().allMatch(token -> sorted.stream().anyMatch(item -> matchesSequenceToken(item, token)));
            return eligibleByScore(rule, matched ? sorted : List.of());
        }
        if ("temporal_ordered".equals(ruleType)) {
            List<String> sequence = jsonList(rule.getSequenceJson());
            boolean matched = sequence.isEmpty() ? structuredEvents >= threshold : matchesOrderedSequence(sorted, sequence);
            return eligibleByScore(rule, matched ? sorted : List.of());
        }
        if ("cross_source_chain".equals(ruleType)) {
            long sources = sorted.stream().map(EvidenceCandidate::sourceType).filter(CorrelationService::notBlank).distinct().count();
            List<String> sequence = jsonList(rule.getSequenceJson());
            boolean sequenceMatched = sequence.isEmpty() || sequence.stream().allMatch(token -> sorted.stream().anyMatch(item -> matchesSequenceToken(item, token)));
            return eligibleByScore(rule, sources >= 2 && structuredEvents >= threshold && sequenceMatched ? sorted : List.of());
        }
        return eligibleByScore(rule, structuredEvents >= threshold ? sorted : List.of());
    }

    private List<EvidenceCandidate> eligibleByScore(SocCorrelationRule rule, List<EvidenceCandidate> evidence) {
        if (evidence.isEmpty()) {
            return evidence;
        }
        Integer minScore = rule.getMinScore();
        if (minScore == null || minScore <= 0) {
            return evidence;
        }
        return scoreGroup(evidence) >= minScore ? evidence : List.of();
    }

    private SocIncidentCluster upsertCluster(SocCorrelationRule rule, String groupKey, List<EvidenceCandidate> evidence) {
        String ruleKey = firstNotBlank(rule.getRuleKey(), rule.getRuleCode(), "correlation_rule");
        String correlationKey = ruleKey + "|" + groupKey;
        SocIncidentCluster cluster = clusterMapper.selectByCorrelationKey(correlationKey);
        boolean created = cluster == null;
        if (cluster == null) {
            cluster = new SocIncidentCluster();
            cluster.setClusterNo("CL-" + Math.abs(correlationKey.hashCode()));
            cluster.setCorrelationKey(correlationKey);
            cluster.setStatus("open");
        }
        EvidenceCandidate primary = evidence.stream().filter(item -> notBlank(item.assetIp())).findFirst().orElse(evidence.get(0));
        Set<String> sourceTypes = evidence.stream().map(EvidenceCandidate::sourceType).filter(CorrelationService::notBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        cluster.setTitle(rule.getRuleName() + " - " + firstNotBlank(primary.assetIp(), primary.hostname(), primary.batchId(), "未命名资产"));
        String sourceSummary = String.join(", ", sourceTypes);
        cluster.setSummary("聚合 " + evidence.size() + " 条证据，来源：" + sourceSummary);
        cluster.setRecommendation("建议按事件簇中的证据顺序核对资产、告警、漏洞和工单，仅做人工处置决策。");
        cluster.setSeverity(highestSeverity(evidence));
        cluster.setScore(scoreGroup(evidence));
        cluster.setAssetId(resolveAssetId(primary.assetIp()));
        cluster.setAssetIp(primary.assetIp());
        cluster.setHostname(primary.hostname());
        cluster.setPrimaryAssetIp(primary.assetIp());
        cluster.setPrimaryHostname(primary.hostname());
        cluster.setBatchId(firstValue(evidence, EvidenceCandidate::batchId));
        cluster.setDemoCaseId(firstValue(evidence, EvidenceCandidate::demoCaseId));
        cluster.setSourceSummary(sourceSummary);
        cluster.setSourceTypes(String.join(",", sourceTypes));
        cluster.setEvidenceCount(evidence.size());
        cluster.setEventCount((int) evidence.stream().filter(item -> "external_event".equals(item.evidenceType())).count());
        cluster.setAlertCount((int) evidence.stream().filter(item -> "alert".equals(item.evidenceType())).count());
        cluster.setVulnerabilityCount((int) evidence.stream().filter(item -> "vulnerability".equals(item.evidenceType())).count());
        cluster.setFirstSeenAt(evidence.stream().map(EvidenceCandidate::eventTime).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null));
        cluster.setLastSeenAt(evidence.stream().map(EvidenceCandidate::eventTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
        cluster.setRuleId(rule.getId() == null || rule.getId() < 0 ? null : rule.getId());
        cluster.setRuleKey(ruleKey);
        cluster.setOwnerId(primary.ownerId());
        cluster.setDeptId(primary.deptId());
        if (created) {
            clusterMapper.insert(cluster);
        } else {
            clusterMapper.updateById(cluster);
        }
        return cluster;
    }

    private Long resolveAssetId(String assetIp) {
        if (!notBlank(assetIp)) {
            return null;
        }
        SocAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
        return asset == null ? null : asset.getId();
    }

    private int rewriteEvidence(SocIncidentCluster cluster, List<EvidenceCandidate> candidates) {
        int written = 0;
        for (EvidenceCandidate candidate : candidates) {
            SocIncidentEvidence evidence = evidenceMapper.selectByClusterAndEvidence(cluster.getId(), candidate.evidenceType(), candidate.evidenceId());
            boolean created = evidence == null;
            if (evidence == null) {
                evidence = new SocIncidentEvidence();
                evidence.setClusterId(cluster.getId());
                evidence.setEvidenceType(candidate.evidenceType());
                evidence.setEvidenceId(candidate.evidenceId());
            }
            evidence.setEvidenceUid(candidate.evidenceUid());
            evidence.setSourceType(candidate.sourceType());
            evidence.setEventType(candidate.eventType());
            evidence.setSeverity(candidate.severity());
            evidence.setRuleId(candidate.ruleId());
            evidence.setAssetIp(candidate.assetIp());
            evidence.setHostname(candidate.hostname());
            evidence.setTargetUrl(candidate.targetUrl());
            evidence.setBatchId(candidate.batchId());
            evidence.setDemoCaseId(candidate.demoCaseId());
            evidence.setEventTime(candidate.eventTime());
            evidence.setDeleted(0);
            RelationScore score = relationScore(candidate, cluster);
            evidence.setRelationScore(score.score());
            evidence.setRelationReason(score.reason());
            if (created) {
                evidenceMapper.insert(evidence);
            } else {
                evidenceMapper.updateById(evidence);
            }
            written++;
        }
        return written;
    }

    private EvidenceCandidate fromExternalEvent(SocExternalEvent event) {
        JsonNode json = json(firstNotBlank(event.getNormalizedEvent(), event.getRawEvent()));
        return new EvidenceCandidate("external_event", event.getId(), event.getEventUid(), event.getSourceType(), event.getEventType(),
                event.getSeverity(), event.getRuleId(), event.getAssetIp(), event.getAssetName(), firstNotBlank(event.getTargetUrl(), text(json, "targetUrl"), text(json, "target_url")),
                firstNotBlank(event.getBatchId(), text(json, "batchId"), text(json, "batch_id")),
                firstNotBlank(event.getDemoCaseId(), text(json, "demoCaseId"), text(json, "demo_case_id")),
                event.getEventTime(), event.getOwnerId(), event.getDeptId());
    }

    private EvidenceCandidate fromAlert(SocAlert alert) {
        return new EvidenceCandidate("alert", alert.getId(), alert.getAlertUid(), alert.getSourceType(), alert.getEventType(),
                alert.getSeverity(), alert.getRuleId(), alert.getAssetIp(), alert.getAssetName(), alert.getTargetUrl(),
                alert.getBatchId(), alert.getDemoCaseId(), alert.getEventTime(), alert.getOwnerId(), alert.getDeptId());
    }

    private EvidenceCandidate fromVulnerability(SocVulnerability vulnerability) {
        return new EvidenceCandidate("vulnerability", vulnerability.getId(), vulnerability.getCveId(), firstNotBlank(vulnerability.getSourceType(), "trivy"),
                "vulnerability", vulnerability.getSeverity(), vulnerability.getCveId(), vulnerability.getAssetIp(), vulnerability.getAssetName(),
                null, null, null, vulnerability.getDetectedAt(), vulnerability.getOwnerId(), vulnerability.getDeptId());
    }

    private RelationScore relationScore(EvidenceCandidate candidate, SocIncidentCluster cluster) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (same(candidate.assetIp(), cluster.getPrimaryAssetIp())) {
            score += 30;
            reasons.add("same assetIp +30");
        }
        if (same(candidate.hostname(), cluster.getPrimaryHostname())) {
            score += 20;
            reasons.add("same hostname +20");
        }
        if (same(candidate.batchId(), cluster.getBatchId()) || same(candidate.demoCaseId(), cluster.getDemoCaseId())) {
            score += 25;
            reasons.add("same batch/demo +25");
        }
        if (notBlank(candidate.ruleId())) {
            score += 15;
            reasons.add("same ruleId or explicit rule evidence +15");
        }
        if (notBlank(candidate.targetUrl())) {
            score += 10;
            reasons.add("same targetUrl or URL evidence +10");
        }
        if (candidate.eventTime() != null && cluster.getFirstSeenAt() != null && cluster.getLastSeenAt() != null
                && !candidate.eventTime().isBefore(cluster.getFirstSeenAt().minusMinutes(5))
                && !candidate.eventTime().isAfter(cluster.getLastSeenAt().plusMinutes(5))) {
            score += 15;
            reasons.add("within window +15");
        }
        if (cluster.getSourceTypes() != null && cluster.getSourceTypes().contains(",")
                && Set.of("waf", "zap", "wazuh", "zeek", "suricata").contains(candidate.sourceType())) {
            score += 20;
            reasons.add("cross-source WAF/ZAP/host/network evidence +20");
        }
        if (Set.of("high", "critical").contains(String.valueOf(candidate.severity()).toLowerCase(Locale.ROOT))) {
            score += 10;
            reasons.add("high/critical +10");
        }
        if ("alert".equals(candidate.evidenceType()) || "vulnerability".equals(candidate.evidenceType())) {
            score += 10;
            reasons.add("linked alert/vulnerability +10");
        }
        return new RelationScore(score, String.join("; ", reasons));
    }

    private int scoreGroup(List<EvidenceCandidate> evidence) {
        int base = evidence.stream().mapToInt(item -> severityWeight(item.severity())).max().orElse(30);
        int sourceBonus = (int) evidence.stream().map(EvidenceCandidate::sourceType).distinct().count() * 8;
        int alertBonus = (int) evidence.stream().filter(item -> "alert".equals(item.evidenceType())).count() * 8;
        int vulnerabilityBonus = (int) evidence.stream().filter(item -> "vulnerability".equals(item.evidenceType())).count() * 6;
        return Math.min(100, base + sourceBonus + alertBonus + vulnerabilityBonus);
    }

    private String groupKey(EvidenceCandidate candidate, SocCorrelationRule rule, int timeframeSeconds) {
        List<String> fields = jsonList(firstNotBlank(rule.getGroupByFieldsJson(), rule.getGroupByJson()));
        if (fields.isEmpty()) {
            fields = List.of("assetIp", "batchId", "demoCaseId");
        }
        List<String> parts = fields.stream()
                .map(field -> {
                    String value = fieldValue(candidate, field);
                    return notBlank(value) ? normalizeFieldName(field) + "=" + value : null;
                })
                .filter(Objects::nonNull)
                .toList();
        String scope = String.join("|", parts);
        boolean hasAssetScope = parts.stream().anyMatch(part -> part.startsWith("assetip=") || part.startsWith("hostname="));
        if (!hasAssetScope) {
            scope = "asset=" + firstNotBlank(candidate.assetIp(), candidate.hostname(), candidate.evidenceUid(), "unknown")
                    + (scope.isEmpty() ? "" : "|" + scope);
        }
        long bucket = candidate.eventTime() == null ? 0 : candidate.eventTime().atZone(ZoneId.systemDefault()).toEpochSecond() / timeframeSeconds;
        return scope + "|" + bucket;
    }

    private void applyRule(SocCorrelationRule rule, CorrelationRuleRequest request) {
        rule.setRuleCode(request.ruleKey());
        rule.setRuleKey(request.ruleKey());
        rule.setRuleName(request.ruleName());
        rule.setRuleType(request.ruleType());
        rule.setSourceTypesJson(emptyToNull(request.sourceTypesJson()));
        rule.setEventTypesJson(emptyToNull(request.eventTypesJson()));
        rule.setGroupByFieldsJson(emptyToNull(request.groupByJson()));
        rule.setGroupByJson(emptyToNull(request.groupByJson()));
        rule.setMinCount(request.threshold());
        rule.setThreshold(request.threshold());
        rule.setTimeWindowMinutes(request.timeframeSeconds() == null ? null : Math.max(1, request.timeframeSeconds() / 60));
        rule.setTimeframeSeconds(request.timeframeSeconds());
        rule.setSequenceJson(emptyToNull(request.sequenceJson()));
        rule.setSeverityMin(emptyToNull(request.severityFloor()));
        rule.setSeverityFloor(emptyToNull(request.severityFloor()));
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setVersion(request.version() == null ? 1 : request.version());
        rule.setDescription(emptyToNull(request.description()));
        rule.setSafetyNote(emptyToNull(request.safetyNote()));
        if (notBlank(request.status())) {
            rule.setStatus(request.status());
        }
    }

    private void validateRuleRequest(CorrelationRuleRequest request) {
        validateRuleShape(request.ruleType(), request.sourceTypesJson(), request.eventTypesJson(), request.groupByJson(),
                request.sequenceJson(), request.threshold(), request.timeframeSeconds(), request.severityFloor());
    }

    private int threshold(SocCorrelationRule rule) {
        Integer value = rule.getMinCount() == null ? rule.getThreshold() : rule.getMinCount();
        return value == null || value < 1 ? 2 : value;
    }

    private int timeframeSeconds(SocCorrelationRule rule) {
        if (rule.getTimeWindowMinutes() != null && rule.getTimeWindowMinutes() > 0) {
            return Math.min(604800, rule.getTimeWindowMinutes() * 60);
        }
        return rule.getTimeframeSeconds() == null || rule.getTimeframeSeconds() <= 0 ? 1800 : rule.getTimeframeSeconds();
    }

    private String severityFloor(SocCorrelationRule rule) {
        return firstNotBlank(rule.getSeverityMin(), rule.getSeverityFloor());
    }

    private void validateRuleShape(String ruleType, String sourceTypesJson, String eventTypesJson, String groupByJson,
                                   String sequenceJson, Integer threshold, Integer timeframeSeconds, String severityFloor) {
        if (!RULE_TYPES.contains(ruleType)) {
            throw new BusinessException("不支持的关联规则类型");
        }
        if (threshold == null || threshold < 1 || threshold > 1000) {
            throw new BusinessException("threshold 必须在 1 到 1000 之间");
        }
        if (timeframeSeconds == null || timeframeSeconds < 60 || timeframeSeconds > 604800) {
            throw new BusinessException("timeframeSeconds 必须在 60 到 604800 之间");
        }
        if (notBlank(severityFloor) && !SEVERITIES.contains(severityFloor.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("severityFloor 不合法");
        }
        for (String json : Arrays.asList(sourceTypesJson, eventTypesJson, groupByJson, sequenceJson)) {
            if (!notBlank(json)) {
                continue;
            }
            String lower = json.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_CONFIG_TOKENS.stream().anyMatch(lower::contains)) {
                throw new BusinessException("关联规则不允许脚本、外部查询或命令执行配置");
            }
            try {
                JsonNode node = objectMapper.readTree(json);
                if (!node.isArray()) {
                    throw new BusinessException("关联规则 JSON 字段必须是数组");
                }
            } catch (JsonProcessingException ex) {
                throw new BusinessException("关联规则 JSON 格式不合法");
            }
        }
    }

    private LambdaQueryWrapper<SocIncidentCluster> scopedClusterWrapper() {
        LambdaQueryWrapper<SocIncidentCluster> wrapper = new LambdaQueryWrapper<SocIncidentCluster>().eq(SocIncidentCluster::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocIncidentCluster::getOwnerId, SocIncidentCluster::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocAlert> scopedAlertWrapper() {
        LambdaQueryWrapper<SocAlert> wrapper = new LambdaQueryWrapper<SocAlert>().eq(SocAlert::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocExternalEvent> scopedExternalEventWrapper() {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>().eq(SocExternalEvent::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocVulnerability> scopedVulnerabilityWrapper() {
        LambdaQueryWrapper<SocVulnerability> wrapper = new LambdaQueryWrapper<SocVulnerability>().eq(SocVulnerability::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocVulnerability::getOwnerId, SocVulnerability::getDeptId);
        return wrapper;
    }

    private void ensureAccess(Long ownerId, Long deptId, String message) {
        if (!securityScope.canAccess(ownerId, deptId)) {
            throw new BusinessException(message);
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

    private Set<String> jsonSet(String json) {
        JsonNode node = json(json);
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        node.forEach(item -> values.add(item.asText("").toLowerCase(Locale.ROOT)));
        return values;
    }

    private List<String> jsonList(String json) {
        JsonNode node = json(json);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = emptyToNull(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        });
        return values;
    }

    private boolean meetsSeverityFloor(EvidenceCandidate candidate, String severityFloor) {
        return !notBlank(severityFloor) || severityWeight(candidate.severity()) >= severityWeight(severityFloor);
    }

    private String distinctValueKey(EvidenceCandidate candidate) {
        return firstNotBlank(candidate.targetUrl(), candidate.ruleId(), candidate.eventType(), candidate.sourceType(), candidate.evidenceUid());
    }

    private boolean matchesOrderedSequence(List<EvidenceCandidate> sorted, List<String> sequence) {
        int cursor = 0;
        for (EvidenceCandidate candidate : sorted) {
            if (cursor < sequence.size() && matchesSequenceToken(candidate, sequence.get(cursor))) {
                cursor++;
            }
        }
        return cursor == sequence.size();
    }

    private boolean matchesSequenceToken(EvidenceCandidate candidate, String token) {
        String expected = String.valueOf(token).trim().toLowerCase(Locale.ROOT);
        return same(expected, candidate.sourceType())
                || same(expected, candidate.eventType())
                || same(expected, candidate.ruleId())
                || same(expected, candidate.evidenceType());
    }

    private String fieldValue(EvidenceCandidate candidate, String field) {
        return switch (normalizeFieldName(field)) {
            case "assetip" -> candidate.assetIp();
            case "hostname", "assetname" -> candidate.hostname();
            case "batchid" -> candidate.batchId();
            case "democaseid" -> candidate.demoCaseId();
            case "sourcetype" -> candidate.sourceType();
            case "eventtype" -> candidate.eventType();
            case "ruleid" -> candidate.ruleId();
            case "targeturl" -> candidate.targetUrl();
            default -> null;
        };
    }

    private String normalizeFieldName(String field) {
        return String.valueOf(field).trim().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private JsonNode json(String value) {
        if (!notBlank(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return node == null ? null : emptyToNull(node.path(field).asText(null));
    }

    private String highestSeverity(List<EvidenceCandidate> evidence) {
        return evidence.stream()
                .map(EvidenceCandidate::severity)
                .filter(CorrelationService::notBlank)
                .max(Comparator.comparingInt(CorrelationService::severityWeight))
                .orElse("medium");
    }

    private static int severityWeight(String severity) {
        return switch (String.valueOf(severity).toLowerCase(Locale.ROOT)) {
            case "critical" -> 90;
            case "high" -> 75;
            case "medium" -> 55;
            case "low" -> 35;
            default -> 20;
        };
    }

    private static String firstValue(List<EvidenceCandidate> evidence, java.util.function.Function<EvidenceCandidate, String> getter) {
        return evidence.stream().map(getter).filter(CorrelationService::notBlank).findFirst().orElse(null);
    }

    private static boolean same(String left, String right) {
        return notBlank(left) && notBlank(right) && left.equalsIgnoreCase(right);
    }

    private static String emptyToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record CorrelateResult(int upsertedClusters, int createdClusters, int evidenceWritten, int activeRules) {
    }

    public record ValidationResult(boolean passed, String message) {
    }

    private record RelationScore(int score, String reason) {
    }

    private record EvidenceCandidate(
            String evidenceType,
            Long evidenceId,
            String evidenceUid,
            String sourceType,
            String eventType,
            String severity,
            String ruleId,
            String assetIp,
            String hostname,
            String targetUrl,
            String batchId,
            String demoCaseId,
            LocalDateTime eventTime,
            Long ownerId,
            Long deptId
    ) {
    }
}
