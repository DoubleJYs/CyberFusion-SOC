package com.zhangjiyan.template.soc.operations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.correlation.SocIncidentClusterMapper;
import com.zhangjiyan.template.soc.keeper.SocClientCheckup;
import com.zhangjiyan.template.soc.keeper.SocClientCheckupMapper;
import com.zhangjiyan.template.soc.keeper.SocClientRecommendationAction;
import com.zhangjiyan.template.soc.keeper.SocClientRecommendationActionMapper;
import com.zhangjiyan.template.soc.notification.SocNotificationLog;
import com.zhangjiyan.template.soc.notification.SocNotificationLogMapper;
import com.zhangjiyan.template.soc.playbook.SocPlaybookMatchLog;
import com.zhangjiyan.template.soc.playbook.SocPlaybookMatchLogMapper;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.recommendation.RecommendationService;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshot;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshotMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.trend.TrendAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocOperationsService {

    private static final Set<String> CLOSED_INCIDENT_STATUS = Set.of("closed", "ignored");
    private static final Set<String> CLOSED_TICKET_STATUS = Set.of("已关闭", "已归档", "closed", "resolved");
    private static final Set<String> COMPLETED_TASK_STATUS = Set.of("completed", "confirmed", "skipped");
    private static final Set<String> ADOPTED_RECOMMENDATION_ACTIONS = Set.of("confirm", "confirmed", "submitted", "apply_playbook", "ticket");

    private final SocIncidentClusterMapper incidentMapper;
    private final SocAssetMapper assetMapper;
    private final SocAssetRiskSnapshotMapper snapshotMapper;
    private final SocTicketMapper ticketMapper;
    private final SocTicketTaskMapper taskMapper;
    private final SocPlaybookMatchLogMapper playbookMatchLogMapper;
    private final SocClientCheckupMapper checkupMapper;
    private final SocClientRecommendationActionMapper recommendationActionMapper;
    private final SocNotificationLogMapper notificationLogMapper;
    private final RecommendationService recommendationService;
    private final TrendAnomalyService trendAnomalyService;
    private final SocSecurityScope securityScope;

    public OperationsOverview overview() {
        TicketSlaMetrics sla = slaMetrics();
        RiskTrendMetrics riskTrend = riskTrendMetrics();
        RecommendationAdoptionMetrics recommendation = recommendationAdoptionMetrics();
        ClientTaskMetrics clientTasks = clientTaskMetrics();
        List<TrendSourceMetric> trendSources = trendSourceMetrics(5);
        List<TopRiskAsset> topRiskAssets = topRiskAssets(5);
        List<TopIncidentCluster> topIncidents = topIncidents(5);

        List<OperationMetric> metrics = new ArrayList<>();
        metrics.add(metric("incident.open.count", "当前开放事件簇数", openIncidentCount(),
                "open", "状态不是 closed/ignored 的事件簇数量。", "/soc/incidents"));
        metrics.add(metric("incident.high_risk.count", "高危事件簇数", highRiskIncidentCount(),
                "risk", "开放事件簇中 severity 为 critical/high 或 score >= 70 的数量。", "/soc/incidents"));
        metrics.add(metric("asset.high_risk.top_count", "Top 高风险资产数", topRiskAssets.size(),
                "risk", "按当前资产风险分排序后进入 Top 列表的资产数量。", "/soc/assets"));
        metrics.add(metric("risk.change.24h", "24h 风险变化", riskTrend.change24h(),
                riskTrend.change24h() >= 0 ? "up" : "down", "最近 24 小时风险快照均值相对前一窗口的变化。", "/soc/assets"));
        metrics.add(metric("risk.change.7d", "7d 风险变化", riskTrend.change7d(),
                riskTrend.change7d() >= 0 ? "up" : "down", "最近风险快照均值相对 7 天窗口起点的变化。", "/soc/assets"));
        metrics.add(metric("ticket.total.count", "工单总数", sla.totalTickets(),
                "ticket", "当前数据范围内的全部工单数量。", "/soc/tickets"));
        metrics.add(metric("ticket.pending.count", "待处理工单", sla.pendingTickets(),
                sla.pendingTickets() > 0 ? "attention" : "stable", "未关闭、未归档的工单数量。", "/soc/tickets"));
        metrics.add(metric("ticket.overdue.count", "超时工单", sla.overdueTickets(),
                sla.overdueTickets() > 0 ? "risk" : "stable", "超过 dueAt 但仍未关闭的工单数量。", "/soc/tickets"));
        metrics.add(metric("ticket.close.rate", "工单关闭率", sla.closeRate(),
                "percent", "已关闭/归档工单占全部工单的比例。", "/soc/tickets"));
        metrics.add(metric("ticket.mtta.hours", "平均响应时间 MTTA", sla.mttaHours(),
                "hours", "从工单创建到首次任务开始/更新时间的平均小时数。", "/soc/tickets"));
        metrics.add(metric("ticket.mttr.hours", "平均处置时间 MTTR", sla.mttrHours(),
                "hours", "从工单创建到关闭/归档的平均小时数。", "/soc/tickets"));
        metrics.add(metric("recommendation.total.count", "推荐动作数量", recommendation.totalRecommendations(),
                "recommendation", "根据事件簇、漏洞、工单和任务计算的当前推荐动作数量。", "/soc/dashboard"));
        metrics.add(metric("recommendation.adopted.count", "已采纳推荐动作", recommendation.adoptedRecommendations(),
                "recommendation", "已记录 confirm、ticket、apply_playbook 等采纳动作的推荐数量。", "/soc/dashboard"));
        metrics.add(metric("recommendation.adoption.rate", "推荐动作采纳率", recommendation.adoptionRate(),
                "percent", "已采纳推荐动作占当前推荐动作的比例。", "/soc/dashboard"));
        metrics.add(metric("playbook.application.count", "处置剧本应用数量", sla.playbookApplications(),
                "playbook", "已应用处置剧本并写入匹配日志的次数。", "/soc/tickets"));
        metrics.add(metric("playbook.completion.rate", "处置剧本完成率", sla.playbookCompletionRate(),
                "percent", "剧本任务中 completed/confirmed/skipped 的比例。", "/soc/tickets"));
        metrics.add(metric("client_task.total.count", "员工待办总数", clientTasks.totalTasks(),
                "client", "分派给员工的全部待办任务数量。", "/soc/client-security"));
        metrics.add(metric("client_task.completed.count", "员工待办完成数", clientTasks.completedTasks(),
                "client", "员工待办中已完成、已确认或已跳过的数量。", "/soc/client-security"));
        metrics.add(metric("client_task.completion.rate", "员工待办完成率", clientTasks.completionRate(),
                "percent", "员工待办中已完成、已确认或已跳过的比例。", "/soc/client-security"));
        metrics.add(metric("client_task.overdue.count", "员工待办逾期数", clientTasks.overdueTasks(),
                clientTasks.overdueTasks() > 0 ? "risk" : "stable", "创建超过 24 小时仍未完成的员工待办数量。", "/soc/client-security"));
        metrics.add(metric("client_checkup.coverage.rate", "安全管家体检覆盖率", clientTasks.checkupCoverageRate(),
                "percent", "有体检记录的资产占当前资产总数的比例。", "/soc/client-security"));
        metrics.add(metric("trend.anomaly.count", "趋势异常数量", trendSources.stream().mapToLong(TrendSourceMetric::currentCount).sum(),
                "trend", "趋势异常 Top 来源在当前窗口中的信号数量汇总。", "/soc/external-events"));

        return new OperationsOverview(metrics, sla, riskTrend, recommendation, clientTasks,
                topRiskAssets, topIncidents, trendSources, LocalDateTime.now());
    }

    public TicketSlaMetrics slaMetrics() {
        LocalDateTime now = LocalDateTime.now();
        List<SocTicket> tickets = ticketMapper.selectList(scopedTicketWrapper().eq(SocTicket::getDeleted, 0));
        long total = tickets.size();
        long pending = tickets.stream().filter(ticket -> !isClosedTicket(ticket)).count();
        long overdue = tickets.stream()
                .filter(ticket -> !isClosedTicket(ticket))
                .filter(ticket -> ticket.getDueAt() != null && ticket.getDueAt().isBefore(now))
                .count();
        List<SocTicket> closed = tickets.stream().filter(this::isClosedTicket).toList();
        long closeRate = total == 0 ? 100 : Math.round(closed.size() * 100.0 / total);
        long mttrHours = Math.round(closed.stream()
                .filter(ticket -> ticket.getCreatedAt() != null && closeTime(ticket) != null)
                .mapToLong(ticket -> Math.max(1, Duration.between(ticket.getCreatedAt(), closeTime(ticket)).toHours()))
                .average()
                .orElse(0));

        Set<Long> visibleTicketIds = tickets.stream().map(SocTicket::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<SocTicketTask> tasks = visibleTicketIds.isEmpty()
                ? List.of()
                : taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getDeleted, 0)
                .in(SocTicketTask::getTicketId, visibleTicketIds)
                .last("LIMIT 2000"));
        Map<Long, List<SocTicketTask>> tasksByTicket = tasks.stream()
                .filter(task -> task.getTicketId() != null)
                .collect(Collectors.groupingBy(SocTicketTask::getTicketId));
        long mttaHours = Math.round(tickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null)
                .mapToLong(ticket -> Math.max(0, Duration.between(ticket.getCreatedAt(), responseTime(ticket, tasksByTicket.get(ticket.getId()))).toHours()))
                .average()
                .orElse(0));

        long playbookApplications = playbookMatchLogMapper.selectCount(new LambdaQueryWrapper<SocPlaybookMatchLog>()
                .eq(SocPlaybookMatchLog::getDeleted, 0));
        long playbookTasks = tasks.stream().filter(task -> task.getPlaybookId() != null).count();
        long completedPlaybookTasks = tasks.stream()
                .filter(task -> task.getPlaybookId() != null)
                .filter(task -> COMPLETED_TASK_STATUS.contains(normalize(task.getStatus())))
                .count();
        long playbookCompletionRate = playbookTasks == 0 ? 100 : Math.round(completedPlaybookTasks * 100.0 / playbookTasks);
        return new TicketSlaMetrics(total, pending, overdue, closeRate, mttaHours, mttrHours,
                playbookApplications, playbookTasks, completedPlaybookTasks, playbookCompletionRate);
    }

    public RiskTrendMetrics riskTrendMetrics() {
        List<SocAsset> assets = scopedAssets();
        Set<Long> assetIds = assets.stream().map(SocAsset::getId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RiskTrendPoint> points = riskTrendPoints(assetIds);
        int latest = points.isEmpty() ? 0 : points.get(points.size() - 1).averageScore();
        int previousDay = points.size() < 2 ? latest : points.get(points.size() - 2).averageScore();
        int first = points.isEmpty() ? latest : points.get(0).averageScore();
        return new RiskTrendMetrics(points, latest - previousDay, latest - first);
    }

    public RecommendationAdoptionMetrics recommendationAdoptionMetrics() {
        List<RecommendationService.RecommendationItem> recommendations;
        try {
            recommendations = recommendationService.topRecommendations(200);
        } catch (Exception ignored) {
            recommendations = List.of();
        }
        Set<String> keys = recommendations.stream().map(RecommendationService.RecommendationItem::key).collect(Collectors.toSet());
        LambdaQueryWrapper<SocClientRecommendationAction> wrapper = new LambdaQueryWrapper<SocClientRecommendationAction>()
                .eq(SocClientRecommendationAction::getDeleted, 0)
                .orderByDesc(SocClientRecommendationAction::getCreatedAt)
                .last("LIMIT 1000");
        securityScope.applyDataScope(wrapper, SocClientRecommendationAction::getOwnerId, SocClientRecommendationAction::getDeptId);
        List<SocClientRecommendationAction> actions = recommendationActionMapper.selectList(wrapper);
        long adopted = actions.stream()
                .filter(action -> keys.isEmpty() || keys.contains(action.getRecommendationKey()))
                .filter(action -> ADOPTED_RECOMMENDATION_ACTIONS.contains(normalize(action.getActionType())))
                .map(SocClientRecommendationAction::getRecommendationKey)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long total = recommendations.size();
        long viewed = actions.stream()
                .filter(action -> keys.isEmpty() || keys.contains(action.getRecommendationKey()))
                .map(SocClientRecommendationAction::getRecommendationKey)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long rate = total == 0 ? 0 : Math.round(adopted * 100.0 / total);
        return new RecommendationAdoptionMetrics(total, adopted, viewed, rate);
    }

    public ClientTaskMetrics clientTaskMetrics() {
        LocalDateTime overdueBefore = LocalDateTime.now().minusHours(24);
        List<SocTicketTask> employeeTasks = taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getDeleted, 0)
                .eq(SocTicketTask::getAssigneeType, "employee")
                .last("LIMIT 2000"));
        if (!securityScope.canViewAllData()) {
            employeeTasks = employeeTasks.stream()
                    .filter(task -> securityScope.canAccess(task.getAssigneeId(), null))
                    .toList();
        }
        long total = employeeTasks.size();
        long completed = employeeTasks.stream().filter(task -> COMPLETED_TASK_STATUS.contains(normalize(task.getStatus()))).count();
        long overdue = employeeTasks.stream()
                .filter(task -> !COMPLETED_TASK_STATUS.contains(normalize(task.getStatus())))
                .filter(task -> task.getCreatedAt() != null && task.getCreatedAt().isBefore(overdueBefore))
                .count();
        long completionRate = total == 0 ? 100 : Math.round(completed * 100.0 / total);
        List<SocAsset> assets = scopedAssets();
        long checkedAssets = checkedAssetCount(assets);
        long coverageRate = assets.isEmpty() ? 0 : Math.round(checkedAssets * 100.0 / assets.size());
        return new ClientTaskMetrics(total, completed, overdue, completionRate, assets.size(), checkedAssets, coverageRate);
    }

    public String reportSummaryLine() {
        OperationsOverview overview = overview();
        TicketSlaMetrics sla = overview.sla();
        RecommendationAdoptionMetrics recommendation = overview.recommendationAdoption();
        ClientTaskMetrics clientTasks = overview.clientTasks();
        RiskTrendMetrics riskTrend = overview.riskTrend();
        long dryRunNotifications = notificationLogMapper.selectCount(new LambdaQueryWrapper<SocNotificationLog>()
                .eq(SocNotificationLog::getDeleted, 0)
                .eq(SocNotificationLog::getStatus, "DRY_RUN"));
        return "运营指标：24h 风险变化 " + signed(riskTrend.change24h())
                + "，7d 风险变化 " + signed(riskTrend.change7d())
                + "，工单关闭率 " + sla.closeRate() + "%，MTTA " + sla.mttaHours() + "h，MTTR " + sla.mttrHours() + "h"
                + "，推荐采纳率 " + recommendation.adoptionRate() + "%"
                + "，员工待办完成率 " + clientTasks.completionRate() + "%"
                + "，通知 dry-run " + dryRunNotifications + " 条";
    }

    private long openIncidentCount() {
        return incidentMapper.selectCount(scopedIncidentWrapper()
                .eq(SocIncidentCluster::getDeleted, 0)
                .notIn(SocIncidentCluster::getStatus, CLOSED_INCIDENT_STATUS));
    }

    private long highRiskIncidentCount() {
        return incidentMapper.selectCount(scopedIncidentWrapper()
                .eq(SocIncidentCluster::getDeleted, 0)
                .notIn(SocIncidentCluster::getStatus, CLOSED_INCIDENT_STATUS)
                .and(w -> w.in(SocIncidentCluster::getSeverity, List.of("critical", "high"))
                        .or().ge(SocIncidentCluster::getScore, 70)));
    }

    private List<TopRiskAsset> topRiskAssets(int limit) {
        return scopedAssets().stream()
                .sorted(Comparator.comparingInt((SocAsset asset) -> nz(asset.getRiskScore())).reversed())
                .limit(Math.max(1, Math.min(limit, 20)))
                .map(asset -> new TopRiskAsset(asset.getId(), asset.getHostname(), asset.getIp(), asset.getRiskLevel(),
                        nz(asset.getRiskScore()), asset.getDeptName(), "/soc/assets?keyword=" + safe(asset.getIp())))
                .toList();
    }

    private List<TopIncidentCluster> topIncidents(int limit) {
        return incidentMapper.selectList(scopedIncidentWrapper()
                        .eq(SocIncidentCluster::getDeleted, 0)
                        .orderByDesc(SocIncidentCluster::getScore)
                        .orderByDesc(SocIncidentCluster::getUpdatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 20))))
                .stream()
                .map(item -> new TopIncidentCluster(item.getId(), item.getClusterNo(), item.getTitle(),
                        firstNotBlank(item.getAssetIp(), item.getPrimaryAssetIp(), item.getHostname()),
                        item.getSeverity(), nz(item.getScore()), nz(item.getEvidenceCount()), item.getStatus(),
                        "/soc/incidents?keyword=" + safe(firstNotBlank(item.getClusterNo(), String.valueOf(item.getId())))))
                .toList();
    }

    private List<TrendSourceMetric> trendSourceMetrics(int limit) {
        try {
            return trendAnomalyService.topAnomalies(limit).stream()
                    .map(item -> new TrendSourceMetric(item.title(), item.assetIp(), item.sourceType(), item.eventType(),
                            item.severity(), item.currentCount(), item.anomalyScore(), item.reason(),
                            "/soc/external-events?assetIp=" + safe(item.assetIp())))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SocAsset> scopedAssets() {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getDeleted, 0)
                .orderByDesc(SocAsset::getRiskScore)
                .orderByDesc(SocAsset::getLastSeenAt)
                .last("LIMIT 1000");
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return assetMapper.selectList(wrapper);
    }

    private List<RiskTrendPoint> riskTrendPoints(Set<Long> assetIds) {
        if (assetIds.isEmpty()) {
            List<RiskTrendPoint> empty = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                empty.add(new RiskTrendPoint(LocalDate.now().minusDays(i).toString(), 0, 0));
            }
            return empty;
        }
        LocalDate start = LocalDate.now().minusDays(6);
        List<SocAssetRiskSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .ge(SocAssetRiskSnapshot::getCalculatedAt, start.atStartOfDay())
                .in(SocAssetRiskSnapshot::getAssetId, assetIds)
                .orderByAsc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 3000"));
        Map<LocalDate, List<SocAssetRiskSnapshot>> byDay = snapshots.stream()
                .filter(item -> item.getCalculatedAt() != null)
                .collect(Collectors.groupingBy(item -> item.getCalculatedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));
        List<RiskTrendPoint> points = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            List<SocAssetRiskSnapshot> rows = byDay.getOrDefault(day, List.of());
            int avg = rows.isEmpty() ? 0 : (int) Math.round(rows.stream().mapToInt(item -> nz(item.getScore())).average().orElse(0));
            points.add(new RiskTrendPoint(day.toString(), avg, rows.size()));
        }
        return points;
    }

    private long checkedAssetCount(List<SocAsset> assets) {
        Set<String> assetKeys = assets.stream()
                .map(asset -> firstNotBlank(asset.getIp(), asset.getHostname(), asset.getId() == null ? null : String.valueOf(asset.getId())))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (assetKeys.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<SocClientCheckup> wrapper = new LambdaQueryWrapper<SocClientCheckup>()
                .eq(SocClientCheckup::getDeleted, 0)
                .orderByDesc(SocClientCheckup::getCheckedAt)
                .last("LIMIT 2000");
        securityScope.applyDataScope(wrapper, SocClientCheckup::getOwnerId, SocClientCheckup::getDeptId);
        return checkupMapper.selectList(wrapper).stream()
                .map(item -> firstNotBlank(item.getAssetIp(), item.getAssetName(), item.getAssetId() == null ? null : String.valueOf(item.getAssetId())))
                .filter(assetKeys::contains)
                .distinct()
                .count();
    }

    private LocalDateTime responseTime(SocTicket ticket, List<SocTicketTask> tasks) {
        if (tasks != null) {
            LocalDateTime taskTime = tasks.stream()
                    .map(task -> firstNonNull(task.getStartedAt(), task.getCompletedAt(), task.getUpdatedAt()))
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            if (taskTime != null) {
                return taskTime;
            }
        }
        return firstNonNull(ticket.getUpdatedAt(), ticket.getCreatedAt(), LocalDateTime.now());
    }

    private LocalDateTime closeTime(SocTicket ticket) {
        return firstNonNull(ticket.getClosedAt(), ticket.getArchivedAt());
    }

    private boolean isClosedTicket(SocTicket ticket) {
        return CLOSED_TICKET_STATUS.contains(normalize(ticket.getStatus()))
                || ticket.getClosedAt() != null
                || ticket.getArchivedAt() != null;
    }

    private LambdaQueryWrapper<SocTicket> scopedTicketWrapper() {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocIncidentCluster> scopedIncidentWrapper() {
        LambdaQueryWrapper<SocIncidentCluster> wrapper = new LambdaQueryWrapper<>();
        securityScope.applyDataScope(wrapper, SocIncidentCluster::getOwnerId, SocIncidentCluster::getDeptId);
        return wrapper;
    }

    private OperationMetric metric(String code, String name, Object value, String trend, String explanation, String target) {
        return new OperationMetric(code, name, value, trend, explanation, target);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(" ", "%20");
    }

    private String signed(long value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public record OperationMetric(String metricCode, String metricName, Object value, String trend,
                                  String explanation, String drilldownTarget) {
    }

    public record OperationsOverview(List<OperationMetric> metrics,
                                     TicketSlaMetrics sla,
                                     RiskTrendMetrics riskTrend,
                                     RecommendationAdoptionMetrics recommendationAdoption,
                                     ClientTaskMetrics clientTasks,
                                     List<TopRiskAsset> topRiskAssets,
                                     List<TopIncidentCluster> topIncidents,
                                     List<TrendSourceMetric> topTrendSources,
                                     LocalDateTime generatedAt) {
    }

    public record TicketSlaMetrics(long totalTickets, long pendingTickets, long overdueTickets,
                                   long closeRate, long mttaHours, long mttrHours,
                                   long playbookApplications, long playbookTasks,
                                   long completedPlaybookTasks, long playbookCompletionRate) {
    }

    public record RiskTrendMetrics(List<RiskTrendPoint> points, int change24h, int change7d) {
    }

    public record RiskTrendPoint(String date, int averageScore, int snapshotCount) {
    }

    public record RecommendationAdoptionMetrics(long totalRecommendations, long adoptedRecommendations,
                                                long viewedRecommendations, long adoptionRate) {
    }

    public record ClientTaskMetrics(long totalTasks, long completedTasks, long overdueTasks,
                                    long completionRate, long totalAssets, long checkedAssets,
                                    long checkupCoverageRate) {
    }

    public record TopRiskAsset(Long assetId, String hostname, String assetIp, String riskLevel,
                               int riskScore, String deptName, String drilldownTarget) {
    }

    public record TopIncidentCluster(Long incidentId, String clusterNo, String title, String asset,
                                     String severity, int score, int evidenceCount, String status,
                                     String drilldownTarget) {
    }

    public record TrendSourceMetric(String title, String assetIp, String sourceType, String eventType,
                                    String severity, long currentCount, int anomalyScore,
                                    String explanation, String drilldownTarget) {
    }
}
