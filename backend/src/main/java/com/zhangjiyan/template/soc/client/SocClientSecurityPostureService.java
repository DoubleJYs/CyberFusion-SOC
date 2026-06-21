package com.zhangjiyan.template.soc.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.keeper.SocClientCheckup;
import com.zhangjiyan.template.soc.keeper.SocClientCheckupMapper;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocClientSecurityPostureService {

    private static final Set<String> CLOSED_TASK_STATUS = Set.of("confirmed", "completed", "skipped");
    private static final Set<String> LOCAL_CHECK_EVENT_TYPES = Set.of("terminal", "host_snapshot");

    private final SocAssetMapper assetMapper;
    private final SocClientCheckupMapper checkupMapper;
    private final SocTicketTaskMapper taskMapper;
    private final SocTicketMapper ticketMapper;
    private final SocAlertMapper alertMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocSecurityScope securityScope;

    public ClientSecurityPosture overview() {
        List<SocAsset> assets = scopedAssets();
        List<SocClientCheckup> checkups = scopedCheckups();
        Map<String, List<SocClientCheckup>> checkupsByAsset = groupCheckupsByAsset(checkups);
        Map<String, SocClientCheckup> latestCheckupByAsset = latestCheckups(checkupsByAsset);
        List<SocTicketTask> pendingTasks = scopedPendingEmployeeTasks();
        List<SocExternalEvent> localChecks = scopedLocalCheckEvents();

        ClientSecurityMetrics metrics = metrics(assets, latestCheckupByAsset, pendingTasks, localChecks);
        List<ClientRiskDownAsset> riskDownAssets = riskDownAssets(checkupsByAsset);
        Map<String, Long> pendingTasksByAsset = pendingTasksByAssetIp(pendingTasks);
        List<ClientHighRiskAsset> highRiskAssets = highRiskAssets(assets, latestCheckupByAsset, pendingTasksByAsset);
        List<ClientReviewRecord> reviewRecords = reviewRecords(localChecks);

        return new ClientSecurityPosture(metrics, highRiskAssets, riskDownAssets, reviewRecords);
    }

    private List<SocAsset> scopedAssets() {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<SocAsset>()
                .eq(SocAsset::getDeleted, 0)
                .orderByDesc(SocAsset::getRiskScore)
                .orderByDesc(SocAsset::getLastSeenAt);
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return assetMapper.selectList(wrapper);
    }

    private List<SocClientCheckup> scopedCheckups() {
        LambdaQueryWrapper<SocClientCheckup> wrapper = new LambdaQueryWrapper<SocClientCheckup>()
                .eq(SocClientCheckup::getDeleted, 0)
                .orderByDesc(SocClientCheckup::getCheckedAt)
                .last("LIMIT 1000");
        securityScope.applyDataScope(wrapper, SocClientCheckup::getOwnerId, SocClientCheckup::getDeptId);
        return checkupMapper.selectList(wrapper);
    }

    private List<SocExternalEvent> scopedLocalCheckEvents() {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0)
                .eq(SocExternalEvent::getSourceType, "osquery")
                .in(SocExternalEvent::getEventType, LOCAL_CHECK_EVENT_TYPES)
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 300");
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return externalEventMapper.selectList(wrapper);
    }

    private List<SocTicketTask> scopedPendingEmployeeTasks() {
        List<SocTicketTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getDeleted, 0)
                .eq(SocTicketTask::getAssigneeType, "employee")
                .notIn(SocTicketTask::getStatus, CLOSED_TASK_STATUS)
                .orderByDesc(SocTicketTask::getUpdatedAt)
                .orderByDesc(SocTicketTask::getCreatedAt)
                .last("LIMIT 500"));
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, SocAlert> alerts = alertsById(candidates.stream()
                .map(SocTicketTask::getAlertId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, SocTicket> tickets = ticketsById(candidates.stream()
                .map(SocTicketTask::getTicketId)
                .filter(Objects::nonNull)
                .toList());
        return candidates.stream()
                .filter(task -> canAccessTask(task, alerts.get(task.getAlertId()), tickets.get(task.getTicketId())))
                .toList();
    }

    private boolean canAccessTask(SocTicketTask task, SocAlert alert, SocTicket ticket) {
        if (alert != null) {
            return securityScope.canAccess(alert.getOwnerId(), alert.getDeptId());
        }
        if (ticket != null) {
            return securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId());
        }
        return securityScope.canAccess(task.getAssigneeId(), null);
    }

    private Map<Long, SocAlert> alertsById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return alertMapper.selectList(new LambdaQueryWrapper<SocAlert>().in(SocAlert::getId, ids))
                .stream()
                .collect(Collectors.toMap(SocAlert::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, SocTicket> ticketsById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ticketMapper.selectList(new LambdaQueryWrapper<SocTicket>().in(SocTicket::getId, ids))
                .stream()
                .collect(Collectors.toMap(SocTicket::getId, Function.identity(), (left, right) -> left));
    }

    static ClientSecurityMetrics metrics(List<SocAsset> assets,
                                         Map<String, SocClientCheckup> latestCheckupByAsset,
                                         List<SocTicketTask> pendingTasks,
                                         List<SocExternalEvent> localChecks) {
        int totalAssets = assets.size();
        long checkedAssets = assets.stream()
                .filter(asset -> latestCheckupByAsset.containsKey(asset.getIp()))
                .count();
        int coverageRate = totalAssets == 0 ? 0 : (int) Math.round(checkedAssets * 100.0 / totalAssets);
        long seriousRiskAssets = assets.stream()
                .filter(asset -> isSeriousAsset(asset, latestCheckupByAsset.get(asset.getIp())))
                .count();
        long pendingEmployeeTasks = pendingTasks.stream()
                .filter(task -> !CLOSED_TASK_STATUS.contains(normalize(task.getStatus())))
                .count();
        long waitingReviewRecords = localChecks.stream()
                .filter(SocClientSecurityPostureService::needsReview)
                .count();
        LocalDateTime latestCheckupAt = latestCheckupByAsset.values().stream()
                .map(SocClientCheckup::getCheckedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new ClientSecurityMetrics(totalAssets, (int) checkedAssets, coverageRate,
                (int) seriousRiskAssets, (int) pendingEmployeeTasks, localChecks.size(),
                (int) waitingReviewRecords, latestCheckupAt);
    }

    static List<ClientRiskDownAsset> riskDownAssets(Map<String, List<SocClientCheckup>> checkupsByAsset) {
        List<ClientRiskDownAsset> rows = new ArrayList<>();
        for (List<SocClientCheckup> checkups : checkupsByAsset.values()) {
            if (checkups.size() < 2) {
                continue;
            }
            SocClientCheckup latest = checkups.get(0);
            SocClientCheckup previous = checkups.get(1);
            int latestScore = value(latest.getScore());
            int previousScore = value(previous.getScore());
            if (latestScore < previousScore) {
                rows.add(new ClientRiskDownAsset(latest.getAssetId(), latest.getAssetName(), latest.getAssetIp(),
                        previousScore, latestScore, previous.getStatus(), latest.getStatus(), latest.getCheckedAt()));
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(ClientRiskDownAsset::changedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .toList();
    }

    static List<ClientHighRiskAsset> highRiskAssets(List<SocAsset> assets,
                                                    Map<String, SocClientCheckup> latestCheckupByAsset,
                                                    Map<String, Long> pendingTasksByAsset) {
        return assets.stream()
                .sorted(Comparator.comparingInt((SocAsset asset) -> effectiveScore(asset, latestCheckupByAsset.get(asset.getIp()))).reversed())
                .limit(8)
                .map(asset -> {
                    SocClientCheckup checkup = latestCheckupByAsset.get(asset.getIp());
                    int score = effectiveScore(asset, checkup);
                    return new ClientHighRiskAsset(asset.getId(), asset.getHostname(), asset.getIp(), asset.getOsType(),
                            asset.getOwnerName(), asset.getDeptName(), score, effectiveRiskLevel(asset, checkup),
                            checkup == null ? null : checkup.getStatus(), checkup == null ? null : checkup.getCheckedAt(),
                            value(asset.getOpenAlertCount()), pendingTasksByAsset.getOrDefault(asset.getIp(), 0L).intValue());
                })
                .toList();
    }

    static List<ClientReviewRecord> reviewRecords(List<SocExternalEvent> localChecks) {
        return localChecks.stream()
                .filter(SocClientSecurityPostureService::needsReview)
                .limit(10)
                .map(event -> new ClientReviewRecord(event.getId(), event.getAssetName(), event.getAssetIp(),
                        event.getEventType(), event.getSeverity(), event.getStatus(),
                        firstNotBlank(event.getRuleName(), "本机检查记录"),
                        event.getEventTime()))
                .toList();
    }

    private static Map<String, List<SocClientCheckup>> groupCheckupsByAsset(List<SocClientCheckup> checkups) {
        Map<String, List<SocClientCheckup>> grouped = new LinkedHashMap<>();
        for (SocClientCheckup checkup : checkups) {
            String key = firstNotBlank(checkup.getAssetIp(), String.valueOf(checkup.getAssetId()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(checkup);
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparing(SocClientCheckup::getCheckedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))));
        return grouped;
    }

    private static Map<String, SocClientCheckup> latestCheckups(Map<String, List<SocClientCheckup>> checkupsByAsset) {
        Map<String, SocClientCheckup> latest = new HashMap<>();
        checkupsByAsset.forEach((assetIp, rows) -> {
            if (!rows.isEmpty()) {
                latest.put(assetIp, rows.get(0));
            }
        });
        return latest;
    }

    private Map<String, Long> pendingTasksByAssetIp(List<SocTicketTask> tasks) {
        Map<Long, SocAlert> alerts = alertsById(tasks.stream()
                .map(SocTicketTask::getAlertId)
                .filter(Objects::nonNull)
                .toList());
        return tasks.stream()
                .map(task -> alerts.get(task.getAlertId()))
                .filter(Objects::nonNull)
                .map(SocAlert::getAssetIp)
                .filter(assetIp -> assetIp != null && !assetIp.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static boolean isSeriousAsset(SocAsset asset, SocClientCheckup checkup) {
        if (checkup != null && "serious".equals(normalize(checkup.getStatus()))) {
            return true;
        }
        String riskLevel = normalize(asset.getRiskLevel());
        return "critical".equals(riskLevel) || "high".equals(riskLevel) || value(asset.getRiskScore()) >= 80;
    }

    private static boolean needsReview(SocExternalEvent event) {
        String status = normalize(event.getStatus());
        return status.isBlank() || "new".equals(status) || "reviewing".equals(status);
    }

    private static int effectiveScore(SocAsset asset, SocClientCheckup checkup) {
        return checkup == null || checkup.getScore() == null ? value(asset.getRiskScore()) : checkup.getScore();
    }

    private static String effectiveRiskLevel(SocAsset asset, SocClientCheckup checkup) {
        if (checkup != null && checkup.getStatus() != null) {
            return switch (normalize(checkup.getStatus())) {
                case "serious" -> "critical";
                case "attention" -> "medium";
                case "safe" -> "low";
                default -> firstNotBlank(asset.getRiskLevel(), "unknown");
            };
        }
        return firstNotBlank(asset.getRiskLevel(), "unknown");
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record ClientSecurityPosture(ClientSecurityMetrics metrics,
                                        List<ClientHighRiskAsset> highRiskAssets,
                                        List<ClientRiskDownAsset> riskDownAssets,
                                        List<ClientReviewRecord> reviewRecords) {
    }

    public record ClientSecurityMetrics(int totalAssets,
                                        int checkedAssets,
                                        int checkupCoverageRate,
                                        int seriousRiskAssets,
                                        int pendingEmployeeTasks,
                                        int localCheckSubmissions,
                                        int waitingReviewRecords,
                                        LocalDateTime latestCheckupAt) {
    }

    public record ClientHighRiskAsset(Long assetId,
                                      String hostname,
                                      String assetIp,
                                      String osType,
                                      String ownerName,
                                      String deptName,
                                      int riskScore,
                                      String riskLevel,
                                      String checkupStatus,
                                      LocalDateTime latestCheckupAt,
                                      int openAlerts,
                                      int pendingTasks) {
    }

    public record ClientRiskDownAsset(Long assetId,
                                      String hostname,
                                      String assetIp,
                                      int previousScore,
                                      int currentScore,
                                      String previousStatus,
                                      String currentStatus,
                                      LocalDateTime changedAt) {
    }

    public record ClientReviewRecord(Long eventId,
                                     String assetName,
                                     String assetIp,
                                     String eventType,
                                     String severity,
                                     String status,
                                     String summary,
                                     LocalDateTime occurredAt) {
    }
}
