package com.zhangjiyan.template.soc.risk;

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
import com.zhangjiyan.template.soc.baseline.SocBaselineCheck;
import com.zhangjiyan.template.soc.baseline.SocBaselineCheckMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEvent;
import com.zhangjiyan.template.soc.fim.SocFileIntegrityEventMapper;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
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
public class RiskScoringService {

    private static final Set<String> CLOSED_ALERT_STATUS = Set.of("closed", "ignored", "false_positive");
    private static final Set<String> CLOSED_VULN_STATUS = Set.of("fixed", "accepted");
    private static final Set<String> CLOSED_TICKET_STATUS = Set.of("已关闭", "已归档");
    private static final Set<String> COMPLETED_TASK_STATUS = Set.of("completed", "confirmed");
    private static final Set<String> UNSAFE_TEXT = Set.of("javascript", "script", "spel", "groovy", "sql", "shell", "bash", "powershell", "exec", "eval", "curl", "wget", "nmap", "scan");

    private final SocRiskScoringPolicyMapper policyMapper;
    private final SocAssetRiskSnapshotMapper snapshotMapper;
    private final SocAssetRiskFactorMapper factorMapper;
    private final SocAssetMapper assetMapper;
    private final SocAlertMapper alertMapper;
    private final SocVulnerabilityMapper vulnerabilityMapper;
    private final SocBaselineCheckMapper baselineMapper;
    private final SocFileIntegrityEventMapper fileIntegrityMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocTicketMapper ticketMapper;
    private final SocTicketTaskMapper ticketTaskMapper;
    private final SocSecurityScope securityScope;
    private final ObjectMapper objectMapper;

    public PageResult<SocRiskScoringPolicy> policies(long pageNum, long pageSize, String status, String keyword) {
        LambdaQueryWrapper<SocRiskScoringPolicy> wrapper = policyWrapper()
                .eq(hasText(status), SocRiskScoringPolicy::getStatus, status)
                .and(hasText(keyword), w -> w.like(SocRiskScoringPolicy::getPolicyCode, keyword)
                        .or().like(SocRiskScoringPolicy::getPolicyName, keyword)
                        .or().like(SocRiskScoringPolicy::getDescription, keyword))
                .orderByDesc(SocRiskScoringPolicy::getUpdatedAt);
        return PageResult.from(policyMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.max(1, pageSize)), wrapper));
    }

    public SocRiskScoringPolicy detail(Long id) {
        SocRiskScoringPolicy policy = policyMapper.selectById(id);
        if (policy == null || Objects.equals(policy.getDeleted(), 1)) {
            throw new BusinessException("风险评分策略不存在");
        }
        return policy;
    }

    @Transactional
    public SocRiskScoringPolicy create(RiskScoringPolicyRequest request) {
        validateRequest(request);
        SocRiskScoringPolicy policy = new SocRiskScoringPolicy();
        apply(policy, request, false);
        policy.setVersion(1);
        policy.setCreatedBy(securityScope.currentUserId());
        policy.setUpdatedBy(securityScope.currentUserId());
        policyMapper.insert(policy);
        return policy;
    }

    @Transactional
    public SocRiskScoringPolicy update(Long id, RiskScoringPolicyRequest request) {
        validateRequest(request);
        SocRiskScoringPolicy policy = detail(id);
        apply(policy, request, true);
        policy.setUpdatedBy(securityScope.currentUserId());
        policy.setVersion((policy.getVersion() == null ? 1 : policy.getVersion()) + 1);
        policyMapper.updateById(policy);
        return policy;
    }

    public ValidationResult validateExisting(Long id) {
        validatePolicy(detail(id));
        return new ValidationResult(true, "风险评分策略校验通过：仅包含数字权重和说明文本，不包含脚本或表达式。");
    }

    @Transactional
    public SocRiskScoringPolicy publish(Long id) {
        SocRiskScoringPolicy policy = detail(id);
        validatePolicy(policy);
        policy.setStatus("active");
        policy.setEnabled(1);
        policy.setApprovedBy(securityScope.currentUserId());
        policy.setApprovedAt(LocalDateTime.now());
        policy.setUpdatedBy(securityScope.currentUserId());
        policy.setVersion((policy.getVersion() == null ? 1 : policy.getVersion()) + 1);
        policyMapper.updateById(policy);
        return policy;
    }

    @Transactional
    public SocRiskScoringPolicy disable(Long id) {
        SocRiskScoringPolicy policy = detail(id);
        policy.setStatus("disabled");
        policy.setEnabled(0);
        policy.setUpdatedBy(securityScope.currentUserId());
        policyMapper.updateById(policy);
        return policy;
    }

    @Transactional
    public RecalculateResult recalculateAll() {
        List<SocAsset> assets = assetMapper.selectList(scopedAssetWrapper().orderByAsc(SocAsset::getId));
        int count = 0;
        for (SocAsset asset : assets) {
            recalculate(asset.getId());
            count++;
        }
        return new RecalculateResult(count, "已重新计算 " + count + " 台资产风险画像。");
    }

    @Transactional
    public AssetRiskProfile recalculate(Long assetId) {
        SocAsset asset = assetById(assetId);
        SocRiskScoringPolicy policy = activePolicyOrDefault();
        CalculationInput input = collectInput(asset);
        CalculationResult result = calculate(policy, asset, input);
        LocalDateTime now = LocalDateTime.now();

        SocAssetRiskSnapshot snapshot = new SocAssetRiskSnapshot();
        snapshot.setAssetId(asset.getId());
        snapshot.setAssetIp(asset.getIp());
        snapshot.setHostname(asset.getHostname());
        snapshot.setScore(result.score());
        snapshot.setRiskLevel(result.riskLevel());
        snapshot.setPolicyId(policy.getId());
        snapshot.setCalculatedAt(now);
        snapshot.setFactorSummaryJson(writeSummary(result.summary()));
        snapshot.setRecommendationSummary(result.recommendationSummary());
        snapshotMapper.insert(snapshot);

        for (SocAssetRiskFactor factor : result.factors()) {
            factor.setSnapshotId(snapshot.getId());
            factor.setAssetId(asset.getId());
            factorMapper.insert(factor);
        }

        asset.setRiskScore(result.score());
        asset.setRiskLevel(result.riskLevel());
        assetMapper.updateById(asset);
        return new AssetRiskProfile(asset, snapshot, result.factors(), history(asset.getId()), result.recommendationSummary(), result.statusReason(), input);
    }

    public AssetRiskProfile profile(Long assetId) {
        SocAsset asset = assetById(assetId);
        SocAssetRiskSnapshot snapshot = latestSnapshot(asset.getId());
        if (snapshot == null) {
            return recalculate(assetId);
        }
        List<SocAssetRiskFactor> factors = factors(snapshot.getId());
        CalculationInput input = collectInput(asset);
        String statusReason = factors.isEmpty()
                ? "当前暂无主要风险因子。"
                : factors.stream().max(Comparator.comparing(SocAssetRiskFactor::getFactorScore)).map(SocAssetRiskFactor::getExplanation).orElse("当前风险画像已生成。");
        return new AssetRiskProfile(asset, snapshot, factors, history(asset.getId()), snapshot.getRecommendationSummary(), statusReason, input);
    }

    public AssetRiskProfile clientProfile(String ip) {
        SocAsset asset = assetMapper.selectOne(scopedAssetWrapper().eq(SocAsset::getIp, ip).last("LIMIT 1"));
        if (asset == null) {
            throw new BusinessException("当前账号无权查看该电脑风险画像");
        }
        return profile(asset.getId());
    }

    public List<AssetRiskProfile> topAssets(int limit) {
        List<SocAssetRiskSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 200"));
        if (snapshots == null || snapshots.isEmpty()) {
            recalculateAll();
            snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                    .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                    .last("LIMIT 200"));
        }
        Map<Long, SocAssetRiskSnapshot> latest = new LinkedHashMap<>();
        for (SocAssetRiskSnapshot snapshot : snapshots) {
            latest.putIfAbsent(snapshot.getAssetId(), snapshot);
        }
        return latest.values().stream()
                .sorted(Comparator.comparing(SocAssetRiskSnapshot::getScore).reversed())
                .limit(Math.max(1, limit))
                .map(snapshot -> profile(snapshot.getAssetId()))
                .toList();
    }

    public List<SocAssetRiskSnapshot> history(Long assetId) {
        SocAsset asset = assetById(assetId);
        return snapshotMapper.selectList(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .eq(SocAssetRiskSnapshot::getAssetId, asset.getId())
                .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 20"));
    }

    public CalculationResult calculate(SocRiskScoringPolicy policy, SocAsset asset, CalculationInput input) {
        validatePolicy(policy);
        List<SocAssetRiskFactor> factors = new ArrayList<>();
        addFactor(factors, "asset", "资产重要性", assetImportanceScore(policy, asset), assetImportant(asset) ? 1 : 0,
                "asset", asset.getId(), "资产 " + asset.getHostname() + " 被识别为生产或关键资产。", "优先确认该资产责任人、维护窗口和处置 SLA。");
        addFactor(factors, "exposure", "暴露面", input.internetExposed() * nz(policy.getInternetExposedWeight()), input.internetExposed(),
                "asset", asset.getId(), "资产存在网关、IDS 或网络侧证据，说明暴露面需要持续关注。", "优先复核访问入口、网关策略和网络检测记录。");
        addFactor(factors, "alert_critical", "严重告警", input.criticalAlerts() * nz(policy.getCriticalAlertWeight()), input.criticalAlerts(),
                "alert", null, "存在未关闭严重告警。", "优先确认告警详情并转入处置工单。");
        addFactor(factors, "alert_high", "高危告警", input.highAlerts() * nz(policy.getHighAlertWeight()), input.highAlerts(),
                "alert", null, "存在未关闭高危告警。", "优先复核告警来源、影响资产和处置剧本。");
        addFactor(factors, "alert_medium", "中危告警", input.mediumAlerts() * nz(policy.getMediumAlertWeight()), input.mediumAlerts(),
                "alert", null, "存在未关闭中危告警。", "确认是否可合并、降噪或进入工单。");
        addFactor(factors, "vulnerability_critical", "严重漏洞", input.criticalVulnerabilities() * nz(policy.getCriticalVulnerabilityWeight()), input.criticalVulnerabilities(),
                "vulnerability", null, "存在未修复严重漏洞。", "优先确认组件版本、影响范围和升级窗口。");
        addFactor(factors, "vulnerability_high", "高危漏洞", input.highVulnerabilities() * nz(policy.getHighVulnerabilityWeight()), input.highVulnerabilities(),
                "vulnerability", null, "存在未修复高危漏洞。", "安排依赖或系统补丁修复并复测。");
        addFactor(factors, "baseline", "基线失败", input.failedBaselines() * nz(policy.getBaselineFailedWeight()), input.failedBaselines(),
                "baseline", null, "存在未通过基线项。", "按基线整改建议排期修复。");
        addFactor(factors, "fim", "文件变更待复核", input.unreviewedFimEvents() * nz(policy.getFimUnreviewedWeight()), input.unreviewedFimEvents(),
                "fim", null, "存在未复核文件完整性事件。", "确认是否为授权变更并补充说明。");
        addFactor(factors, "external_event", "外部证据", input.highExternalEvents() * nz(policy.getExternalEventWeight()), input.highExternalEvents(),
                "external_event", null, "存在高风险外部事件证据。", "关联告警和资产上下文，确认是否需要处置。");
        addFactor(factors, "ticket_overdue", "超时工单", input.overdueTickets() * nz(policy.getOverdueTicketWeight()), input.overdueTickets(),
                "ticket", null, "存在已超时但未关闭工单。", "优先推动工单状态流转或复核阻塞原因。");
        addFactor(factors, "playbook_open", "未完成剧本任务", input.openPlaybookTasks() * nz(policy.getOpenPlaybookTaskWeight()), input.openPlaybookTasks(),
                "playbook_task", null, "存在未完成处置剧本任务。", "按任务清单完成复核、验证和报告记录。");
        addFactor(factors, "employee_pending", "员工待办", input.employeePendingTasks() * nz(policy.getEmployeePendingTaskWeight()), input.employeePendingTasks(),
                "client_task", null, "存在需要员工确认或提交证据的待办。", "提醒员工提交日志、确认变更或完成本机检查。");
        addFactor(factors, "ticket_closed", "已关闭工单", -input.closedTickets() * nz(policy.getClosedTicketReduceWeight()), input.closedTickets(),
                "ticket", null, "已有工单关闭记录，风险可以适度降低。", "保留关闭结论和验证证据。");
        addFactor(factors, "playbook_completed", "已完成剧本任务", -input.completedPlaybookTasks() * nz(policy.getCompletedPlaybookReduceWeight()), input.completedPlaybookTasks(),
                "playbook_task", null, "处置剧本任务已完成，风险可以适度降低。", "保留任务证据并进入报告。");

        int rawScore = factors.stream().mapToInt(f -> f.getFactorScore() == null ? 0 : f.getFactorScore()).sum();
        int maxScore = Math.max(1, nz(policy.getMaxScore(), 100));
        int score = Math.max(0, Math.min(maxScore, rawScore));
        String level = riskLevel(score);
        List<SocAssetRiskFactor> visibleFactors = factors.stream()
                .filter(factor -> factor.getFactorScore() != null && factor.getFactorScore() != 0)
                .sorted(Comparator.comparing(SocAssetRiskFactor::getFactorScore).reversed())
                .toList();
        String recommendation = visibleFactors.stream()
                .filter(factor -> factor.getFactorScore() > 0)
                .map(SocAssetRiskFactor::getRecommendation)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "；" + right)
                .orElse("当前风险较低，继续保持监控和定期复核。");
        String reason = visibleFactors.stream()
                .filter(factor -> factor.getFactorScore() > 0)
                .findFirst()
                .map(SocAssetRiskFactor::getExplanation)
                .orElse("当前暂无显著风险因子。");
        return new CalculationResult(score, level, visibleFactors, summaryMap(input, score, level), recommendation, reason);
    }

    private CalculationInput collectInput(SocAsset asset) {
        List<SocAlert> alerts = alertMapper.selectList(scopedAlertWrapper()
                .and(w -> w.eq(SocAlert::getAssetIp, asset.getIp()).or().eq(SocAlert::getAssetName, asset.getHostname())));
        List<Long> alertIds = alerts.stream().map(SocAlert::getId).filter(Objects::nonNull).toList();
        long criticalAlerts = alerts.stream().filter(alert -> isOpenAlert(alert) && "critical".equals(alert.getSeverity())).count();
        long highAlerts = alerts.stream().filter(alert -> isOpenAlert(alert) && "high".equals(alert.getSeverity())).count();
        long mediumAlerts = alerts.stream().filter(alert -> isOpenAlert(alert) && "medium".equals(alert.getSeverity())).count();
        long vulnerabilitiesCritical = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper(asset).eq(SocVulnerability::getSeverity, "critical").notIn(SocVulnerability::getStatus, CLOSED_VULN_STATUS));
        long vulnerabilitiesHigh = vulnerabilityMapper.selectCount(scopedVulnerabilityWrapper(asset).eq(SocVulnerability::getSeverity, "high").notIn(SocVulnerability::getStatus, CLOSED_VULN_STATUS));
        long failedBaselines = baselineMapper.selectCount(scopedBaselineWrapper(asset).eq(SocBaselineCheck::getResult, "failed"));
        long fim = fileIntegrityMapper.selectCount(scopedFimWrapper(asset).notIn(SocFileIntegrityEvent::getStatus, List.of("confirmed", "ignored", "closed")));
        long external = externalEventMapper.selectCount(scopedExternalWrapper(asset).in(SocExternalEvent::getSeverity, List.of("critical", "high")).notIn(SocExternalEvent::getStatus, List.of("ignored", "closed")));
        List<SocTicket> tickets = alertIds.isEmpty()
                ? List.of()
                : ticketMapper.selectList(scopedTicketWrapper().in(SocTicket::getAlertId, alertIds));
        LocalDateTime now = LocalDateTime.now();
        long overdueTickets = tickets.stream().filter(ticket -> ticket.getDueAt() != null && ticket.getDueAt().isBefore(now) && !CLOSED_TICKET_STATUS.contains(ticket.getStatus())).count();
        long closedTickets = tickets.stream().filter(ticket -> CLOSED_TICKET_STATUS.contains(ticket.getStatus())).count();
        List<SocTicketTask> tasks = alertIds.isEmpty()
                ? List.of()
                : ticketTaskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>().in(SocTicketTask::getAlertId, alertIds).eq(SocTicketTask::getDeleted, 0));
        long openTasks = tasks.stream().filter(task -> !COMPLETED_TASK_STATUS.contains(task.getStatus()) && !"skipped".equals(task.getStatus())).count();
        long completedTasks = tasks.stream().filter(task -> COMPLETED_TASK_STATUS.contains(task.getStatus())).count();
        long employeePending = tasks.stream().filter(task -> "employee".equals(task.getAssigneeType()) && !COMPLETED_TASK_STATUS.contains(task.getStatus()) && !"skipped".equals(task.getStatus())).count();
        long exposed = external > 0 || alerts.stream().anyMatch(alert -> Set.of("waf", "suricata", "zeek").contains(alert.getSourceType())) ? 1 : 0;
        return new CalculationInput(criticalAlerts, highAlerts, mediumAlerts, vulnerabilitiesCritical, vulnerabilitiesHigh,
                failedBaselines, fim, external, overdueTickets, openTasks, employeePending, closedTickets, completedTasks, exposed);
    }

    private SocRiskScoringPolicy activePolicyOrDefault() {
        try {
            SocRiskScoringPolicy active = policyMapper.selectOne(policyWrapper()
                    .eq(SocRiskScoringPolicy::getStatus, "active")
                    .eq(SocRiskScoringPolicy::getEnabled, 1)
                    .orderByDesc(SocRiskScoringPolicy::getApprovedAt)
                    .last("LIMIT 1"));
            if (active != null) {
                return active;
            }
        } catch (RuntimeException ignored) {
            // fallback below keeps demos usable when existing DBs have not been refreshed.
        }
        return defaultPolicy();
    }

    public static SocRiskScoringPolicy defaultPolicy() {
        SocRiskScoringPolicy policy = new SocRiskScoringPolicy();
        policy.setPolicyCode("BUILTIN_DEFAULT_ASSET_RISK");
        policy.setPolicyName("内置默认资产风险评分策略");
        policy.setDescription("数据库策略不可用时使用的内置数字权重。");
        policy.setStatus("active");
        policy.setEnabled(1);
        policy.setVersion(1);
        policy.setCriticalAssetWeight(10);
        policy.setInternetExposedWeight(10);
        policy.setCriticalAlertWeight(25);
        policy.setHighAlertWeight(15);
        policy.setMediumAlertWeight(8);
        policy.setCriticalVulnerabilityWeight(25);
        policy.setHighVulnerabilityWeight(15);
        policy.setBaselineFailedWeight(8);
        policy.setFimUnreviewedWeight(6);
        policy.setExternalEventWeight(6);
        policy.setOverdueTicketWeight(10);
        policy.setOpenPlaybookTaskWeight(6);
        policy.setEmployeePendingTaskWeight(8);
        policy.setClosedTicketReduceWeight(8);
        policy.setCompletedPlaybookReduceWeight(5);
        policy.setMaxScore(100);
        return policy;
    }

    private void apply(SocRiskScoringPolicy policy, RiskScoringPolicyRequest request, boolean keepStatus) {
        policy.setPolicyCode(request.policyCode().trim());
        policy.setPolicyName(request.policyName().trim());
        policy.setDescription(trimToNull(request.description()));
        policy.setStatus(hasText(request.status()) ? request.status() : keepStatus ? policy.getStatus() : "draft");
        policy.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        SocRiskScoringPolicy defaults = defaultPolicy();
        policy.setCriticalAssetWeight(value(request.criticalAssetWeight(), defaults.getCriticalAssetWeight()));
        policy.setInternetExposedWeight(value(request.internetExposedWeight(), defaults.getInternetExposedWeight()));
        policy.setCriticalAlertWeight(value(request.criticalAlertWeight(), defaults.getCriticalAlertWeight()));
        policy.setHighAlertWeight(value(request.highAlertWeight(), defaults.getHighAlertWeight()));
        policy.setMediumAlertWeight(value(request.mediumAlertWeight(), defaults.getMediumAlertWeight()));
        policy.setCriticalVulnerabilityWeight(value(request.criticalVulnerabilityWeight(), defaults.getCriticalVulnerabilityWeight()));
        policy.setHighVulnerabilityWeight(value(request.highVulnerabilityWeight(), defaults.getHighVulnerabilityWeight()));
        policy.setBaselineFailedWeight(value(request.baselineFailedWeight(), defaults.getBaselineFailedWeight()));
        policy.setFimUnreviewedWeight(value(request.fimUnreviewedWeight(), defaults.getFimUnreviewedWeight()));
        policy.setExternalEventWeight(value(request.externalEventWeight(), defaults.getExternalEventWeight()));
        policy.setOverdueTicketWeight(value(request.overdueTicketWeight(), defaults.getOverdueTicketWeight()));
        policy.setOpenPlaybookTaskWeight(value(request.openPlaybookTaskWeight(), defaults.getOpenPlaybookTaskWeight()));
        policy.setEmployeePendingTaskWeight(value(request.employeePendingTaskWeight(), defaults.getEmployeePendingTaskWeight()));
        policy.setClosedTicketReduceWeight(value(request.closedTicketReduceWeight(), defaults.getClosedTicketReduceWeight()));
        policy.setCompletedPlaybookReduceWeight(value(request.completedPlaybookReduceWeight(), defaults.getCompletedPlaybookReduceWeight()));
        policy.setMaxScore(value(request.maxScore(), defaults.getMaxScore()));
        policy.setDeleted(0);
    }

    private void validateRequest(RiskScoringPolicyRequest request) {
        rejectUnsafeText(request.policyCode(), request.policyName(), request.description());
    }

    private void validatePolicy(SocRiskScoringPolicy policy) {
        rejectUnsafeText(policy.getPolicyCode(), policy.getPolicyName(), policy.getDescription());
        List<Integer> weights = List.of(
                nz(policy.getCriticalAssetWeight()), nz(policy.getInternetExposedWeight()), nz(policy.getCriticalAlertWeight()),
                nz(policy.getHighAlertWeight()), nz(policy.getMediumAlertWeight()), nz(policy.getCriticalVulnerabilityWeight()),
                nz(policy.getHighVulnerabilityWeight()), nz(policy.getBaselineFailedWeight()), nz(policy.getFimUnreviewedWeight()),
                nz(policy.getExternalEventWeight()), nz(policy.getOverdueTicketWeight()), nz(policy.getOpenPlaybookTaskWeight()),
                nz(policy.getEmployeePendingTaskWeight()), nz(policy.getClosedTicketReduceWeight()), nz(policy.getCompletedPlaybookReduceWeight()),
                nz(policy.getMaxScore(), 100)
        );
        if (weights.stream().anyMatch(weight -> weight < 0 || weight > 100)) {
            throw new BusinessException("风险评分权重必须是 0-100 的数字");
        }
        if (nz(policy.getMaxScore(), 100) < 1) {
            throw new BusinessException("风险评分上限必须大于 0");
        }
    }

    private void rejectUnsafeText(String... values) {
        for (String value : values) {
            if (value == null) continue;
            String lower = value.toLowerCase(Locale.ROOT);
            for (String forbidden : UNSAFE_TEXT) {
                if (lower.contains(forbidden)) {
                    throw new BusinessException("风险评分策略只允许数字权重和说明文本，不允许脚本、表达式、SQL、shell、扫描或外部调用语义");
                }
            }
        }
    }

    private SocAsset assetById(Long assetId) {
        SocAsset asset = assetMapper.selectById(assetId);
        if (asset == null || Objects.equals(asset.getDeleted(), 1) || !securityScope.canAccess(asset.getOwnerId(), asset.getDeptId())) {
            throw new BusinessException("资产不存在或无权访问");
        }
        return asset;
    }

    private SocAssetRiskSnapshot latestSnapshot(Long assetId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .eq(SocAssetRiskSnapshot::getAssetId, assetId)
                .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 1"));
    }

    private List<SocAssetRiskFactor> factors(Long snapshotId) {
        return factorMapper.selectList(new LambdaQueryWrapper<SocAssetRiskFactor>()
                .eq(SocAssetRiskFactor::getSnapshotId, snapshotId)
                .orderByDesc(SocAssetRiskFactor::getFactorScore));
    }

    private LambdaQueryWrapper<SocRiskScoringPolicy> policyWrapper() {
        return new LambdaQueryWrapper<SocRiskScoringPolicy>().eq(SocRiskScoringPolicy::getDeleted, 0);
    }

    private LambdaQueryWrapper<SocAsset> scopedAssetWrapper() {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<SocAsset>().eq(SocAsset::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocAlert> scopedAlertWrapper() {
        LambdaQueryWrapper<SocAlert> wrapper = new LambdaQueryWrapper<SocAlert>().eq(SocAlert::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocVulnerability> scopedVulnerabilityWrapper(SocAsset asset) {
        LambdaQueryWrapper<SocVulnerability> wrapper = new LambdaQueryWrapper<SocVulnerability>()
                .eq(SocVulnerability::getDeleted, 0)
                .and(w -> w.eq(SocVulnerability::getAssetIp, asset.getIp()).or().eq(SocVulnerability::getAssetName, asset.getHostname()));
        securityScope.applyDataScope(wrapper, SocVulnerability::getOwnerId, SocVulnerability::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocBaselineCheck> scopedBaselineWrapper(SocAsset asset) {
        LambdaQueryWrapper<SocBaselineCheck> wrapper = new LambdaQueryWrapper<SocBaselineCheck>()
                .eq(SocBaselineCheck::getDeleted, 0)
                .and(w -> w.eq(SocBaselineCheck::getAssetIp, asset.getIp()).or().eq(SocBaselineCheck::getAssetName, asset.getHostname()));
        securityScope.applyDataScope(wrapper, SocBaselineCheck::getOwnerId, SocBaselineCheck::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocFileIntegrityEvent> scopedFimWrapper(SocAsset asset) {
        LambdaQueryWrapper<SocFileIntegrityEvent> wrapper = new LambdaQueryWrapper<SocFileIntegrityEvent>()
                .eq(SocFileIntegrityEvent::getDeleted, 0)
                .and(w -> w.eq(SocFileIntegrityEvent::getAssetIp, asset.getIp()).or().eq(SocFileIntegrityEvent::getHostname, asset.getHostname()));
        securityScope.applyDataScope(wrapper, SocFileIntegrityEvent::getOwnerId, SocFileIntegrityEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocExternalEvent> scopedExternalWrapper(SocAsset asset) {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .and(w -> w.eq(SocExternalEvent::getAssetIp, asset.getIp())
                        .or().eq(SocExternalEvent::getAssetName, asset.getHostname())
                        .or().eq(SocExternalEvent::getSrcIp, asset.getIp())
                        .or().eq(SocExternalEvent::getDestIp, asset.getIp()));
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocTicket> scopedTicketWrapper() {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<SocTicket>().eq(SocTicket::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
        return wrapper;
    }

    private boolean isOpenAlert(SocAlert alert) {
        return alert.getStatus() == null || !CLOSED_ALERT_STATUS.contains(alert.getStatus());
    }

    private boolean assetImportant(SocAsset asset) {
        String value = (asset.getHostname() + " " + asset.getRiskLevel() + " " + asset.getSourceType()).toLowerCase(Locale.ROOT);
        return value.contains("prod") || value.contains("core") || value.contains("critical") || value.contains("finance");
    }

    private int assetImportanceScore(SocRiskScoringPolicy policy, SocAsset asset) {
        if (!assetImportant(asset)) return 0;
        return nz(policy.getCriticalAssetWeight());
    }

    private void addFactor(List<SocAssetRiskFactor> factors, String type, String name, long score, long count,
                           String bizType, Long bizId, String explanation, String recommendation) {
        if (score == 0 || count == 0) return;
        SocAssetRiskFactor factor = new SocAssetRiskFactor();
        factor.setFactorType(type);
        factor.setFactorName(name);
        factor.setFactorScore((int) Math.max(-100, Math.min(100, score)));
        factor.setFactorCount((int) Math.min(Integer.MAX_VALUE, count));
        factor.setRelatedBizType(bizType);
        factor.setRelatedBizId(bizId);
        factor.setExplanation(explanation + " 数量：" + count + "，影响分：" + score + "。");
        factor.setRecommendation(recommendation);
        factors.add(factor);
    }

    private Map<String, Object> summaryMap(CalculationInput input, int score, String level) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("score", score);
        map.put("riskLevel", level);
        map.put("criticalAlerts", input.criticalAlerts());
        map.put("highAlerts", input.highAlerts());
        map.put("mediumAlerts", input.mediumAlerts());
        map.put("criticalVulnerabilities", input.criticalVulnerabilities());
        map.put("highVulnerabilities", input.highVulnerabilities());
        map.put("failedBaselines", input.failedBaselines());
        map.put("unreviewedFimEvents", input.unreviewedFimEvents());
        map.put("highExternalEvents", input.highExternalEvents());
        map.put("overdueTickets", input.overdueTickets());
        map.put("openPlaybookTasks", input.openPlaybookTasks());
        map.put("employeePendingTasks", input.employeePendingTasks());
        return map;
    }

    private String writeSummary(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String riskLevel(int score) {
        if (score >= 80) return "critical";
        if (score >= 60) return "high";
        if (score >= 30) return "medium";
        return "low";
    }

    private int value(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private int nz(Integer value) {
        return nz(value, 0);
    }

    private int nz(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    public record ValidationResult(boolean passed, String message) {
    }

    public record RecalculateResult(int recalculatedAssets, String message) {
    }

    public record AssetRiskProfile(SocAsset asset, SocAssetRiskSnapshot snapshot, List<SocAssetRiskFactor> factors,
                                   List<SocAssetRiskSnapshot> history, String recommendationSummary,
                                   String statusReason, CalculationInput counts) {
    }

    public record CalculationInput(long criticalAlerts, long highAlerts, long mediumAlerts,
                                   long criticalVulnerabilities, long highVulnerabilities,
                                   long failedBaselines, long unreviewedFimEvents, long highExternalEvents,
                                   long overdueTickets, long openPlaybookTasks, long employeePendingTasks,
                                   long closedTickets, long completedPlaybookTasks, long internetExposed) {
    }

    public record CalculationResult(int score, String riskLevel, List<SocAssetRiskFactor> factors,
                                    Map<String, Object> summary, String recommendationSummary, String statusReason) {
    }
}
