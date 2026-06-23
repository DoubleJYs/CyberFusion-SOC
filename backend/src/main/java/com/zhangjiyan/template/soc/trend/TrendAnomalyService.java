package com.zhangjiyan.template.soc.trend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrendAnomalyService {

    private static final int MAX_SIGNAL_ROWS = 3000;
    private static final int DEFAULT_LIMIT = 10;
    private static final double VOLUME_SPIKE_RATIO = 2.5;
    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
    private static final Set<String> CROSS_SOURCE_TYPES = Set.of("waf", "zap", "wazuh", "suricata", "zeek");

    private final SocExternalEventMapper externalEventMapper;
    private final SocAlertMapper alertMapper;
    private final SocSecurityScope securityScope;

    public List<TrendAnomalyItem> topAnomalies(int limit) {
        return anomalies(new TrendAnomalyQuery(null, null, null, null, null, normalizedLimit(limit)));
    }

    public List<TrendAnomalyItem> anomalies(TrendAnomalyQuery query) {
        List<TrendSignal> signals = loadSignals();
        LocalDateTime referenceTime = latestReferenceTime(signals);
        return detect(signals, referenceTime, query == null ? TrendAnomalyQuery.empty() : query);
    }

    public List<TrendAggregationItem> aggregations(TrendAggregationQuery query) {
        TrendAggregationQuery effective = query == null ? TrendAggregationQuery.empty() : query;
        List<TrendSignal> signals = filterSignals(loadSignals(), effective.toAnomalyQuery());
        Map<String, MutableAggregation> grouped = new LinkedHashMap<>();
        for (TrendSignal signal : signals) {
            String bucket = bucket(signal.eventTime(), effective.granularity());
            String key = String.join("|", bucket, safe(signal.assetIp()), safe(signal.sourceType()),
                    safe(signal.eventType()), safe(signal.ruleId()), safe(signal.severity()));
            grouped.computeIfAbsent(key, ignored -> new MutableAggregation(bucket, effective.granularity(),
                            safe(signal.assetIp()), safe(signal.sourceType()), safe(signal.eventType()),
                            safe(signal.ruleId()), safe(signal.severity())))
                    .count++;
        }
        return grouped.values().stream()
                .map(MutableAggregation::toItem)
                .sorted(Comparator.comparing(TrendAggregationItem::bucket).reversed()
                        .thenComparing(TrendAggregationItem::count, Comparator.reverseOrder()))
                .limit(normalizedLimit(effective.limit()))
                .toList();
    }

    static List<TrendAnomalyItem> detectForTest(List<TrendSignal> signals, LocalDateTime referenceTime,
                                                TrendAnomalyQuery query) {
        return detect(signals, referenceTime, query == null ? TrendAnomalyQuery.empty() : query);
    }

    private static List<TrendAnomalyItem> detect(List<TrendSignal> sourceSignals, LocalDateTime referenceTime,
                                                 TrendAnomalyQuery query) {
        List<TrendSignal> signals = filterSignals(sourceSignals, query);
        if (signals.isEmpty()) {
            return List.of();
        }
        LocalDateTime windowEnd = referenceTime == null ? latestReferenceTime(signals) : referenceTime;
        LocalDateTime currentStart = windowEnd.minusHours(24);
        LocalDateTime baselineStart = currentStart.minusDays(7);
        List<TrendSignal> current = signals.stream()
                .filter(signal -> !signal.eventTime().isBefore(currentStart) && signal.eventTime().isBefore(windowEnd))
                .toList();
        List<TrendSignal> baseline = signals.stream()
                .filter(signal -> !signal.eventTime().isBefore(baselineStart) && signal.eventTime().isBefore(currentStart))
                .toList();
        if (current.isEmpty()) {
            return List.of();
        }

        Map<String, SignalGroup> currentGroups = groupSignals(current, true);
        Map<String, SignalGroup> baselineGroups = groupSignals(baseline, true);
        List<TrendAnomalyItem> result = new ArrayList<>();
        currentGroups.forEach((key, group) -> addVolumeSpike(result, group, baselineGroups.get(key), currentStart, windowEnd));
        addSeverityRatioAnomalies(result, current, baseline, currentStart, windowEnd);
        addConsecutiveAssetAnomalies(result, current, baseline, currentStart, windowEnd);
        addCrossSourceAnomalies(result, current, baseline, currentStart, windowEnd);

        return result.stream()
                .sorted(Comparator.comparing(TrendAnomalyItem::anomalyScore).reversed()
                        .thenComparing(TrendAnomalyItem::currentCount, Comparator.reverseOrder())
                        .thenComparing(TrendAnomalyItem::assetIp, Comparator.nullsLast(String::compareTo)))
                .limit(normalizedLimit(query.limit()))
                .toList();
    }

    private List<TrendSignal> loadSignals() {
        LocalDateTime start = LocalDateTime.now().minusDays(8);
        List<TrendSignal> result = new ArrayList<>();

        LambdaQueryWrapper<SocExternalEvent> eventWrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .ge(SocExternalEvent::getEventTime, start)
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT " + MAX_SIGNAL_ROWS);
        securityScope.applyDataScope(eventWrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        for (SocExternalEvent event : externalEventMapper.selectList(eventWrapper)) {
            if (event.getEventTime() == null) {
                continue;
            }
            result.add(new TrendSignal(
                    firstNotBlank(event.getAssetIp(), event.getDestIp()),
                    normalize(event.getSourceType()),
                    normalize(event.getEventType()),
                    event.getRuleId(),
                    normalizeSeverity(event.getSeverity()),
                    event.getEventTime(),
                    "external_event"
            ));
        }

        LambdaQueryWrapper<SocAlert> alertWrapper = new LambdaQueryWrapper<SocAlert>()
                .eq(SocAlert::getDeleted, 0)
                .ge(SocAlert::getEventTime, start)
                .orderByDesc(SocAlert::getEventTime)
                .last("LIMIT " + MAX_SIGNAL_ROWS);
        securityScope.applyDataScope(alertWrapper, SocAlert::getOwnerId, SocAlert::getDeptId);
        for (SocAlert alert : alertMapper.selectList(alertWrapper)) {
            if (alert.getEventTime() == null) {
                continue;
            }
            result.add(new TrendSignal(
                    alert.getAssetIp(),
                    normalize(alert.getSourceType()),
                    normalize(alert.getEventType()),
                    alert.getRuleId(),
                    normalizeSeverity(alert.getSeverity()),
                    alert.getEventTime(),
                    "alert"
            ));
        }
        return result;
    }

    private static void addVolumeSpike(List<TrendAnomalyItem> result, SignalGroup current, SignalGroup baseline,
                                       LocalDateTime windowStart, LocalDateTime windowEnd) {
        long currentCount = current.count();
        double baselineCount = baseline == null ? 0.0 : baseline.count() / 7.0;
        double ratio = changeRatio(currentCount, baselineCount);
        boolean noBaselineSpike = baselineCount == 0.0 && currentCount >= 2;
        boolean ratioSpike = baselineCount > 0.0 && ratio >= VOLUME_SPIKE_RATIO && currentCount >= Math.max(2, baselineCount * VOLUME_SPIKE_RATIO);
        if (!noBaselineSpike && !ratioSpike) {
            return;
        }
        int score = boundedScore(42 + (int) Math.round(Math.min(ratio, 10.0) * 6) + severityScore(current.maxSeverity()));
        String title = "%s / %s 数量突增".formatted(display(current.sourceType), display(current.eventType));
        String reason = "当前 24 小时窗口出现 %d 条，7 天日均 %.1f 条，变化倍数 %.1fx；分组字段 assetIp=%s、sourceType=%s、eventType=%s、ruleId=%s。"
                .formatted(currentCount, baselineCount, ratio, display(current.assetIp), display(current.sourceType),
                        display(current.eventType), display(current.ruleId));
        String recommendation = "先复核该资产近期同类证据和关联告警，确认是否需要进入事件簇或工单处置。";
        result.add(new TrendAnomalyItem(title, current.assetIp, current.sourceType, current.eventType,
                current.maxSeverity(), currentCount, roundOne(baselineCount), roundOne(ratio), score,
                reason, recommendation, windowStart, windowEnd));
    }

    private static void addSeverityRatioAnomalies(List<TrendAnomalyItem> result, List<TrendSignal> current,
                                                  List<TrendSignal> baseline, LocalDateTime windowStart,
                                                  LocalDateTime windowEnd) {
        Map<String, SignalGroup> currentByAsset = groupSignals(current, false);
        Map<String, SignalGroup> baselineByAsset = groupSignals(baseline, false);
        currentByAsset.forEach((assetIp, group) -> {
            long currentCount = group.count();
            if (currentCount < 2) {
                return;
            }
            double currentRatio = group.highOrCriticalCount() * 1.0 / currentCount;
            SignalGroup baselineGroup = baselineByAsset.get(assetIp);
            double baselineRatio = baselineGroup == null || baselineGroup.count() == 0 ? 0.0
                    : baselineGroup.highOrCriticalCount() * 1.0 / baselineGroup.count();
            if (currentRatio < 0.5 || currentRatio < baselineRatio + 0.35) {
                return;
            }
            int score = boundedScore(48 + (int) Math.round((currentRatio - baselineRatio) * 45) + severityScore(group.maxSeverity()));
            String sourceType = sourceSummary(group.sources());
            String title = "%s 严重级别占比升高".formatted(display(group.assetIp));
            String reason = "当前窗口 high/critical 占比 %.0f%%，基线占比 %.0f%%，高风险信号 %d/%d；来源覆盖 %s。"
                    .formatted(currentRatio * 100, baselineRatio * 100, group.highOrCriticalCount(), currentCount, sourceType);
            String recommendation = "优先查看该资产的高危告警、漏洞和外部证据，必要时提升处置优先级。";
            result.add(new TrendAnomalyItem(title, group.assetIp, sourceType, "severity_ratio_rise",
                    group.maxSeverity(), currentCount, roundOne((baselineGroup == null ? 0 : baselineGroup.count()) / 7.0),
                    roundOne(changeRatio(currentCount, baselineGroup == null ? 0.0 : baselineGroup.count() / 7.0)),
                    score, reason, recommendation, windowStart, windowEnd));
        });
    }

    private static void addConsecutiveAssetAnomalies(List<TrendAnomalyItem> result, List<TrendSignal> current,
                                                     List<TrendSignal> baseline, LocalDateTime windowStart,
                                                     LocalDateTime windowEnd) {
        Map<String, Map<String, Long>> bucketsByAsset = new LinkedHashMap<>();
        for (TrendSignal signal : current) {
            bucketsByAsset.computeIfAbsent(safe(signal.assetIp()), ignored -> new LinkedHashMap<>())
                    .merge(bucket(signal.eventTime(), "hour"), 1L, Long::sum);
        }
        Map<String, SignalGroup> currentByAsset = groupSignals(current, false);
        Map<String, SignalGroup> baselineByAsset = groupSignals(baseline, false);
        bucketsByAsset.forEach((assetIp, buckets) -> {
            if (buckets.size() < 3) {
                return;
            }
            SignalGroup group = currentByAsset.get(assetIp);
            SignalGroup baselineGroup = baselineByAsset.get(assetIp);
            long currentCount = group == null ? 0 : group.count();
            double baselineCount = baselineGroup == null ? 0.0 : baselineGroup.count() / 7.0;
            int score = boundedScore(50 + buckets.size() * 7 + severityScore(group == null ? "medium" : group.maxSeverity()));
            String reason = "同一资产在当前 24 小时内覆盖 %d 个小时窗口，累计 %d 条信号，7 天日均 %.1f 条。"
                    .formatted(buckets.size(), currentCount, baselineCount);
            result.add(new TrendAnomalyItem(display(assetIp) + " 连续异常", assetIp,
                    group == null ? "multi_source" : sourceSummary(group.sources()), "continuous_activity",
                    group == null ? "medium" : group.maxSeverity(), currentCount, roundOne(baselineCount),
                    roundOne(changeRatio(currentCount, baselineCount)), score, reason,
                    "检查该资产是否存在持续触发的任务、策略误配或真实风险扩散，并关联近期事件簇。", windowStart, windowEnd));
        });
    }

    private static void addCrossSourceAnomalies(List<TrendAnomalyItem> result, List<TrendSignal> current,
                                                List<TrendSignal> baseline, LocalDateTime windowStart,
                                                LocalDateTime windowEnd) {
        Map<String, SignalGroup> currentByAsset = groupSignals(current, false);
        Map<String, SignalGroup> baselineByAsset = groupSignals(baseline, false);
        currentByAsset.forEach((assetIp, group) -> {
            Set<String> coveredSources = new LinkedHashSet<>(group.sources());
            coveredSources.retainAll(CROSS_SOURCE_TYPES);
            if (coveredSources.size() < 3 || group.count() < 3) {
                return;
            }
            SignalGroup baselineGroup = baselineByAsset.get(assetIp);
            double baselineCount = baselineGroup == null ? 0.0 : baselineGroup.count() / 7.0;
            int score = boundedScore(58 + coveredSources.size() * 8 + severityScore(group.maxSeverity()));
            String sourceType = String.join("+", coveredSources);
            String reason = "同一资产当前窗口同时出现 %s 等 %d 类来源，累计 %d 条，7 天日均 %.1f 条。"
                    .formatted(sourceType, coveredSources.size(), group.count(), baselineCount);
            result.add(new TrendAnomalyItem(display(assetIp) + " 跨源同时上升", assetIp,
                    "multi_source", "cross_source_rise", group.maxSeverity(), group.count(), roundOne(baselineCount),
                    roundOne(changeRatio(group.count(), baselineCount)), score, reason,
                    "将 WAF、Web 风险、主机和网络检测证据合并复核，优先确认是否属于同一事件簇。", windowStart, windowEnd));
        });
    }

    private static Map<String, SignalGroup> groupSignals(List<TrendSignal> signals, boolean fullKey) {
        Map<String, SignalGroup> groups = new LinkedHashMap<>();
        for (TrendSignal signal : signals) {
            String key = fullKey
                    ? String.join("|", safe(signal.assetIp()), safe(signal.sourceType()), safe(signal.eventType()),
                    safe(signal.ruleId()), safe(signal.severity()))
                    : safe(signal.assetIp());
            groups.computeIfAbsent(key, ignored -> new SignalGroup(signal.assetIp(), signal.sourceType(),
                            signal.eventType(), signal.ruleId(), signal.severity()))
                    .add(signal);
        }
        return groups;
    }

    private static List<TrendSignal> filterSignals(List<TrendSignal> signals, TrendAnomalyQuery query) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        TrendAnomalyQuery effective = query == null ? TrendAnomalyQuery.empty() : query;
        return signals.stream()
                .filter(signal -> matches(effective.assetIp(), signal.assetIp()))
                .filter(signal -> matches(effective.sourceType(), signal.sourceType()))
                .filter(signal -> matches(effective.eventType(), signal.eventType()))
                .filter(signal -> matches(effective.ruleId(), signal.ruleId()))
                .filter(signal -> matches(effective.severity(), signal.severity()))
                .filter(signal -> signal.eventTime() != null)
                .toList();
    }

    private static boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || safe(actual).equalsIgnoreCase(expected.trim());
    }

    private static LocalDateTime latestReferenceTime(List<TrendSignal> signals) {
        Optional<LocalDateTime> latest = signals.stream()
                .map(TrendSignal::eventTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo);
        return latest.map(time -> time.plusMinutes(1)).orElseGet(LocalDateTime::now);
    }

    private static String bucket(LocalDateTime time, String granularity) {
        if ("day".equalsIgnoreCase(granularity)) {
            return LocalDate.from(time).toString();
        }
        return time.format(HOUR_BUCKET);
    }

    private static int normalizedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 100);
    }

    private static double changeRatio(long currentCount, double baselineCount) {
        double denominator = baselineCount <= 0.0 ? 0.5 : baselineCount;
        return currentCount / denominator;
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static int boundedScore(int score) {
        return Math.max(1, Math.min(100, score));
    }

    private static int severityScore(String severity) {
        return switch (normalizeSeverity(severity)) {
            case "critical" -> 20;
            case "high" -> 14;
            case "medium" -> 7;
            default -> 2;
        };
    }

    private static boolean highOrCritical(String severity) {
        return "critical".equals(normalizeSeverity(severity)) || "high".equals(normalizeSeverity(severity));
    }

    private static String normalizeSeverity(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "medium" : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String sourceSummary(Set<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return "multi_source";
        }
        return String.join("+", sources);
    }

    public record TrendAnomalyQuery(String assetIp, String sourceType, String eventType, String ruleId,
                                    String severity, Integer limit) {
        public static TrendAnomalyQuery empty() {
            return new TrendAnomalyQuery(null, null, null, null, null, DEFAULT_LIMIT);
        }
    }

    public record TrendAggregationQuery(String assetIp, String sourceType, String eventType, String ruleId,
                                        String severity, String granularity, Integer limit) {
        public static TrendAggregationQuery empty() {
            return new TrendAggregationQuery(null, null, null, null, null, "hour", 100);
        }

        private TrendAnomalyQuery toAnomalyQuery() {
            return new TrendAnomalyQuery(assetIp, sourceType, eventType, ruleId, severity, limit);
        }
    }

    public record TrendAnomalyItem(String title, String assetIp, String sourceType, String eventType,
                                   String severity, long currentCount, double baselineCount,
                                   double changeRatio, int anomalyScore, String reason,
                                   String recommendation, LocalDateTime windowStart,
                                   LocalDateTime windowEnd) {
    }

    public record TrendAggregationItem(String bucket, String granularity, String assetIp, String sourceType,
                                       String eventType, String ruleId, String severity, long count) {
    }

    public record TrendSignal(String assetIp, String sourceType, String eventType, String ruleId, String severity,
                              LocalDateTime eventTime, String recordType) {
    }

    private static final class SignalGroup {
        private final String assetIp;
        private final String sourceType;
        private final String eventType;
        private final String ruleId;
        private String maxSeverity;
        private long count;
        private long highOrCriticalCount;
        private final Set<String> sources = new LinkedHashSet<>();

        private SignalGroup(String assetIp, String sourceType, String eventType, String ruleId, String severity) {
            this.assetIp = safe(assetIp);
            this.sourceType = safe(sourceType);
            this.eventType = safe(eventType);
            this.ruleId = safe(ruleId);
            this.maxSeverity = normalizeSeverity(severity);
        }

        private void add(TrendSignal signal) {
            count++;
            if (highOrCritical(signal.severity())) {
                highOrCriticalCount++;
            }
            if (severityScore(signal.severity()) > severityScore(maxSeverity)) {
                maxSeverity = normalizeSeverity(signal.severity());
            }
            if (signal.sourceType() != null && !signal.sourceType().isBlank()) {
                sources.add(signal.sourceType());
            }
        }

        private long count() {
            return count;
        }

        private long highOrCriticalCount() {
            return highOrCriticalCount;
        }

        private String maxSeverity() {
            return maxSeverity;
        }

        private Set<String> sources() {
            return sources;
        }
    }

    private static final class MutableAggregation {
        private final String bucket;
        private final String granularity;
        private final String assetIp;
        private final String sourceType;
        private final String eventType;
        private final String ruleId;
        private final String severity;
        private long count;

        private MutableAggregation(String bucket, String granularity, String assetIp, String sourceType,
                                   String eventType, String ruleId, String severity) {
            this.bucket = bucket;
            this.granularity = "day".equalsIgnoreCase(granularity) ? "day" : "hour";
            this.assetIp = assetIp;
            this.sourceType = sourceType;
            this.eventType = eventType;
            this.ruleId = ruleId;
            this.severity = severity;
        }

        private TrendAggregationItem toItem() {
            return new TrendAggregationItem(bucket, granularity, assetIp, sourceType, eventType, ruleId, severity, count);
        }
    }
}
