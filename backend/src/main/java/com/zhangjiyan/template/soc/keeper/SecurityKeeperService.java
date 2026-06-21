package com.zhangjiyan.template.soc.keeper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.playbook.ResponsePlaybookService;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.playbook.TaskActionRequest;
import com.zhangjiyan.template.soc.risk.RiskScoringService;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityKeeperService {

    private static final List<String> CLOSED_ALERT_STATUS = List.of("closed", "ignored", "false_positive");
    private static final List<String> CLOSED_VULN_STATUS = List.of("fixed", "accepted");

    private final SocOperationService socOperationService;
    private final RiskScoringService riskScoringService;
    private final ResponsePlaybookService playbookService;
    private final SocClientCheckupMapper checkupMapper;
    private final SocClientCheckupItemMapper itemMapper;
    private final SocClientRecommendationActionMapper recommendationActionMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocAlertMapper alertMapper;
    private final SocVulnerabilityMapper vulnerabilityMapper;
    private final SocTicketTaskMapper taskMapper;
    private final SocSecurityScope securityScope;
    private final ObjectMapper objectMapper;

    @Transactional
    public CheckupDetail run(String assetIp) {
        SocOperationService.ClientDeviceProfile profile = socOperationService.clientDeviceProfile(assetIp);
        RiskScoringService.AssetRiskProfile riskProfile = riskScoringService.clientProfile(assetIp);
        SocAsset asset = profile.asset();
        LocalDateTime now = LocalDateTime.now();
        RiskScoringService.CalculationInput counts = riskProfile.counts();
        int score = riskProfile.snapshot() == null || riskProfile.snapshot().getScore() == null
                ? profile.metrics().riskScore()
                : riskProfile.snapshot().getScore();
        String status = statusFromScore(score);

        SocClientCheckup checkup = new SocClientCheckup();
        checkup.setCheckupNo("CK-" + now.toLocalDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        checkup.setAssetId(asset.getId());
        checkup.setAssetIp(asset.getIp());
        checkup.setAssetName(asset.getHostname());
        checkup.setOsType(asset.getOsType());
        checkup.setScore(score);
        checkup.setStatus(status);
        checkup.setSummary(summary(status, riskProfile.statusReason(), profile.metrics()));
        checkup.setRecommendationSummary(firstNotBlank(riskProfile.recommendationSummary(), recommendation(status)));
        checkup.setOwnerId(asset.getOwnerId() == null ? securityScope.currentUserId() : asset.getOwnerId());
        checkup.setDeptId(asset.getDeptId() == null ? securityScope.currentDeptId() : asset.getDeptId());
        checkup.setOperatorName(securityScope.currentUsername());
        checkup.setCheckedAt(now);
        checkup.setDeleted(0);
        checkupMapper.insert(checkup);

        List<CheckupRiskItem> riskItems = buildRiskItems(profile, counts);
        int sort = 10;
        for (CheckupRiskItem riskItem : riskItems) {
            SocClientCheckupItem item = new SocClientCheckupItem();
            item.setCheckupId(checkup.getId());
            item.setItemType(riskItem.itemType());
            item.setItemName(riskItem.itemName());
            item.setSeverity(riskItem.severity());
            item.setItemCount((int) Math.min(Integer.MAX_VALUE, riskItem.count()));
            item.setSummary(riskItem.summary());
            item.setRecommendation(riskItem.recommendation());
            item.setSortOrder(sort);
            itemMapper.insert(item);
            sort += 10;
        }
        writeSecurityLog(checkup, riskItems);
        return new CheckupDetail(checkup, riskItems, recommendations(checkup, riskItems));
    }

    public List<CheckupSummary> history(String assetIp) {
        LambdaQueryWrapper<SocClientCheckup> wrapper = scopedCheckupWrapper()
                .eq(hasText(assetIp), SocClientCheckup::getAssetIp, assetIp)
                .orderByDesc(SocClientCheckup::getCheckedAt)
                .last("LIMIT 30");
        if (hasText(assetIp)) {
            socOperationService.clientDeviceProfile(assetIp);
        }
        return checkupMapper.selectList(wrapper).stream()
                .map(this::summaryOf)
                .toList();
    }

    public CheckupDetail detail(Long id) {
        SocClientCheckup checkup = checkupMapper.selectById(id);
        if (checkup == null || Objects.equals(checkup.getDeleted(), 1)) {
            throw new BusinessException("体检记录不存在");
        }
        if (!securityScope.canAccess(checkup.getOwnerId(), checkup.getDeptId())) {
            throw new BusinessException("无权查看该体检记录");
        }
        socOperationService.clientDeviceProfile(checkup.getAssetIp());
        List<CheckupRiskItem> items = itemMapper.selectList(new LambdaQueryWrapper<SocClientCheckupItem>()
                        .eq(SocClientCheckupItem::getCheckupId, checkup.getId())
                        .orderByAsc(SocClientCheckupItem::getSortOrder)
                        .orderByAsc(SocClientCheckupItem::getId))
                .stream()
                .map(item -> new CheckupRiskItem(item.getItemType(), item.getItemName(), item.getSeverity(),
                        item.getItemCount() == null ? 0 : item.getItemCount(), item.getSummary(), item.getRecommendation()))
                .toList();
        return new CheckupDetail(checkup, items, recommendations(checkup, items));
    }

    public List<SecurityLogItem> logs(String assetIp) {
        SocOperationService.ClientDeviceProfile profile = socOperationService.clientDeviceProfile(assetIp);
        SocAsset asset = profile.asset();
        List<SecurityLogItem> items = new ArrayList<>();

        List<SocClientCheckup> checkups = checkupMapper.selectList(scopedCheckupWrapper()
                .eq(SocClientCheckup::getAssetIp, asset.getIp())
                .orderByDesc(SocClientCheckup::getCheckedAt)
                .last("LIMIT 40"));
        for (SocClientCheckup checkup : checkups) {
            items.add(new SecurityLogItem(
                    "checkup-" + checkup.getId(),
                    "checkup",
                    "已完成一次一键体检",
                    firstNotBlank(checkup.getSummary(), "安全管家已汇总当前电脑风险状态。"),
                    checkup.getStatus(),
                    severityFromStatus(checkup.getStatus()),
                    checkup.getAssetIp(),
                    checkup.getAssetName(),
                    checkup.getCheckedAt()
            ));
        }
        for (int i = 0; i + 1 < checkups.size(); i++) {
            SocClientCheckup current = checkups.get(i);
            SocClientCheckup previous = checkups.get(i + 1);
            if (!Objects.equals(current.getStatus(), previous.getStatus())) {
                items.add(new SecurityLogItem(
                        "risk-status-" + current.getId(),
                        "risk_status_change",
                        "风险状态从" + employeeStatus(previous.getStatus()) + "变为" + employeeStatus(current.getStatus()),
                        "根据最近一次一键体检结果，安全管家更新了当前电脑状态。",
                        current.getStatus(),
                        severityFromStatus(current.getStatus()),
                        current.getAssetIp(),
                        current.getAssetName(),
                        current.getCheckedAt()
                ));
            }
        }

        List<SocExternalEvent> events = externalEventMapper.selectList(scopedExternalEventWrapper()
                .and(w -> w.eq(SocExternalEvent::getAssetIp, asset.getIp())
                        .or().eq(SocExternalEvent::getAssetName, asset.getHostname())
                        .or().eq(SocExternalEvent::getSrcIp, asset.getIp())
                        .or().eq(SocExternalEvent::getDestIp, asset.getIp()))
                .orderByDesc(SocExternalEvent::getEventTime)
                .last("LIMIT 120"));
        for (SocExternalEvent event : events) {
            SecurityLogItem item = eventLogItem(event);
            if (item != null) {
                items.add(item);
            }
        }

        List<SocClientRecommendationAction> actions = recommendationActionMapper.selectList(new LambdaQueryWrapper<SocClientRecommendationAction>()
                .eq(SocClientRecommendationAction::getDeleted, 0)
                .eq(SocClientRecommendationAction::getAssetIp, asset.getIp())
                .orderByDesc(SocClientRecommendationAction::getCreatedAt)
                .last("LIMIT 80"));
        actions = actions.stream()
                .filter(action -> securityScope.canAccess(action.getOwnerId(), action.getDeptId()))
                .toList();
        for (SocClientRecommendationAction action : actions) {
            items.add(new SecurityLogItem(
                    "recommendation-action-" + action.getId(),
                    "employee_confirmation",
                    "confirm".equals(action.getActionType()) ? "已确认一条修复建议" : "已提交处理说明",
                    limit(firstNotBlank(action.getNote(), "员工已补充当前电脑相关处理记录。"), 180),
                    action.getActionType(),
                    "low",
                    action.getAssetIp(),
                    firstNotBlank(action.getAssetName(), asset.getHostname()),
                    action.getCreatedAt()
            ));
        }

        List<Long> currentAlertIds = profile.alerts().stream().map(SocAlert::getId).filter(Objects::nonNull).toList();
        List<SocTicketTask> tasks = currentAlertIds.isEmpty() ? List.of() : taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getAssigneeType, "employee")
                .eq(SocTicketTask::getDeleted, 0)
                .in(SocTicketTask::getAlertId, currentAlertIds)
                .orderByDesc(SocTicketTask::getUpdatedAt)
                .orderByDesc(SocTicketTask::getCreatedAt)
                .last("LIMIT 80"));
        for (SocTicketTask task : tasks) {
            items.add(new SecurityLogItem(
                    "ticket-task-" + task.getId(),
                    "ticket_task",
                    taskStatusTitle(task),
                    firstNotBlank(task.getTaskName(), task.getInstruction(), "安全团队创建了和当前电脑相关的处理任务。"),
                    firstNotBlank(task.getStatus(), "pending"),
                    "warning",
                    asset.getIp(),
                    asset.getHostname(),
                    firstNonNull(task.getUpdatedAt(), task.getCreatedAt())
            ));
        }

        return items.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted(Comparator.comparing(SecurityLogItem::occurredAt).reversed())
                .limit(100)
                .toList();
    }

    public List<RepairRecommendation> recommendations(String assetIp) {
        SocOperationService.ClientDeviceProfile profile = socOperationService.clientDeviceProfile(assetIp);
        SocAsset asset = profile.asset();
        Map<String, SocClientRecommendationAction> latestActions = latestActions(asset.getIp());
        List<Long> currentAlertIds = profile.alerts().stream().map(SocAlert::getId).filter(Objects::nonNull).toList();

        List<RepairRecommendation> taskItems = playbookService.employeeTasks().stream()
                .filter(task -> task.getAlertId() == null || currentAlertIds.contains(task.getAlertId()))
                .map(task -> taskRecommendation(task, asset, latestActions.get(key("task", task.getId()))))
                .toList();

        List<RepairRecommendation> alertItems = profile.alerts().stream()
                .filter(alert -> alert.getId() != null && (alert.getStatus() == null || !CLOSED_ALERT_STATUS.contains(alert.getStatus())))
                .limit(10)
                .map(alert -> alertRecommendation(alert, latestActions.get(key("alert", alert.getId()))))
                .toList();

        List<RepairRecommendation> vulnerabilityItems = profile.vulnerabilities().stream()
                .filter(item -> item.getId() != null && (item.getStatus() == null || !CLOSED_VULN_STATUS.contains(item.getStatus())))
                .limit(10)
                .map(item -> vulnerabilityRecommendation(item, latestActions.get(key("vulnerability", item.getId()))))
                .toList();

        return java.util.stream.Stream.concat(java.util.stream.Stream.concat(taskItems.stream(), alertItems.stream()), vulnerabilityItems.stream())
                .sorted(Comparator.comparingInt(SecurityKeeperService::recommendationPriority))
                .limit(30)
                .toList();
    }

    @Transactional
    public RepairRecommendation submitRecommendationNote(String recommendationId, String note) {
        RecommendationTarget target = resolveRecommendationTarget(recommendationId);
        String safeNote = limit(firstNotBlank(note, "员工提交风险修复说明"), 500);
        if ("task".equals(target.relatedType())) {
            playbookService.submitEmployeeEvidence(target.relatedId(), new TaskActionRequest(safeNote, safeNote));
        }
        SocClientRecommendationAction action = recordRecommendationAction(target, "note", safeNote);
        writeRecommendationLog(target, action);
        return withLatestAction(target.recommendation(), action);
    }

    @Transactional
    public RepairRecommendation confirmRecommendation(String recommendationId, String note) {
        RecommendationTarget target = resolveRecommendationTarget(recommendationId);
        String safeNote = limit(firstNotBlank(note, "员工确认已按建议处理"), 500);
        if ("task".equals(target.relatedType())) {
            playbookService.confirmEmployeeTask(target.relatedId(), new TaskActionRequest(safeNote, safeNote));
        }
        SocClientRecommendationAction action = recordRecommendationAction(target, "confirm", safeNote);
        writeRecommendationLog(target, action);
        return withLatestAction(target.recommendation(), action);
    }

    private Map<String, SocClientRecommendationAction> latestActions(String assetIp) {
        Map<String, SocClientRecommendationAction> result = new LinkedHashMap<>();
        LambdaQueryWrapper<SocClientRecommendationAction> wrapper = new LambdaQueryWrapper<SocClientRecommendationAction>()
                .eq(SocClientRecommendationAction::getDeleted, 0)
                .eq(hasText(assetIp), SocClientRecommendationAction::getAssetIp, assetIp)
                .orderByDesc(SocClientRecommendationAction::getCreatedAt)
                .last("LIMIT 200");
        securityScope.applyDataScope(wrapper, SocClientRecommendationAction::getOwnerId, SocClientRecommendationAction::getDeptId);
        for (SocClientRecommendationAction action : recommendationActionMapper.selectList(wrapper)) {
            result.putIfAbsent(action.getRecommendationKey(), action);
        }
        return result;
    }

    private RecommendationTarget resolveRecommendationTarget(String recommendationId) {
        if (!hasText(recommendationId) || !recommendationId.contains("-")) {
            throw new BusinessException("修复建议不存在");
        }
        String[] parts = recommendationId.split("-", 2);
        Long id = parseId(parts[1]);
        return switch (parts[0]) {
            case "task" -> taskTarget(id);
            case "alert" -> alertTarget(id);
            case "vulnerability" -> vulnerabilityTarget(id);
            default -> throw new BusinessException("修复建议不存在");
        };
    }

    private RecommendationTarget taskTarget(Long taskId) {
        SocTicketTask task = playbookService.employeeTasks().stream()
                .filter(item -> Objects.equals(item.getId(), taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("无权处理该修复建议"));
        SocAlert alert = task.getAlertId() == null ? null : alertMapper.selectById(task.getAlertId());
        String assetIp = alert == null ? "-" : firstNotBlank(alert.getAssetIp(), "-");
        String assetName = alert == null ? null : alert.getAssetName();
        RepairRecommendation recommendation = taskRecommendation(task, fallbackAsset(assetName, assetIp), null);
        return new RecommendationTarget(recommendation, "task", task.getId(), assetIp, assetName,
                task.getAssigneeId(), securityScope.currentDeptId());
    }

    private RecommendationTarget alertTarget(Long alertId) {
        SocAlert alert = alertMapper.selectById(alertId);
        if (alert == null || Objects.equals(alert.getDeleted(), 1) || !securityScope.canAccess(alert.getOwnerId(), alert.getDeptId())) {
            throw new BusinessException("无权处理该修复建议");
        }
        RepairRecommendation recommendation = alertRecommendation(alert, null);
        return new RecommendationTarget(recommendation, "alert", alert.getId(), firstNotBlank(alert.getAssetIp(), "-"),
                alert.getAssetName(), alert.getOwnerId(), alert.getDeptId());
    }

    private RecommendationTarget vulnerabilityTarget(Long vulnerabilityId) {
        SocVulnerability vulnerability = vulnerabilityMapper.selectById(vulnerabilityId);
        if (vulnerability == null || Objects.equals(vulnerability.getDeleted(), 1) || !securityScope.canAccess(vulnerability.getOwnerId(), vulnerability.getDeptId())) {
            throw new BusinessException("无权处理该修复建议");
        }
        RepairRecommendation recommendation = vulnerabilityRecommendation(vulnerability, null);
        return new RecommendationTarget(recommendation, "vulnerability", vulnerability.getId(), firstNotBlank(vulnerability.getAssetIp(), "-"),
                vulnerability.getAssetName(), vulnerability.getOwnerId(), vulnerability.getDeptId());
    }

    private SocAsset fallbackAsset(String assetName, String assetIp) {
        SocAsset asset = new SocAsset();
        asset.setHostname(firstNotBlank(assetName, "当前电脑"));
        asset.setIp(firstNotBlank(assetIp, "-"));
        return asset;
    }

    private SocClientRecommendationAction recordRecommendationAction(RecommendationTarget target, String actionType, String note) {
        SocClientRecommendationAction action = new SocClientRecommendationAction();
        action.setRecommendationKey(target.recommendation().id());
        action.setActionType(actionType);
        action.setNote(note);
        action.setRelatedType(target.relatedType());
        action.setRelatedId(target.relatedId());
        action.setAssetIp(target.assetIp());
        action.setAssetName(target.assetName());
        action.setOwnerId(target.ownerId() == null ? securityScope.currentUserId() : target.ownerId());
        action.setDeptId(target.deptId() == null ? securityScope.currentDeptId() : target.deptId());
        action.setOperatorName(securityScope.currentUsername());
        action.setDeleted(0);
        recommendationActionMapper.insert(action);
        return action;
    }

    private void writeRecommendationLog(RecommendationTarget target, SocClientRecommendationAction action) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("recommendationId", target.recommendation().id());
        normalized.put("actionType", action.getActionType());
        normalized.put("relatedType", target.relatedType());
        normalized.put("relatedId", target.relatedId());
        normalized.put("assetIp", target.assetIp());
        normalized.put("note", action.getNote());

        SocExternalEvent event = new SocExternalEvent();
        event.setEventUid("REPAIR-ACTION-" + action.getId());
        event.setSourceType("client-remediation");
        event.setEventType("repair_guidance_action");
        event.setSeverity("low");
        event.setRuleId("SECURITY-KEEPER-REPAIR");
        event.setRuleName("安全管家风险修复记录");
        event.setAssetName(target.assetName());
        event.setAssetIp(target.assetIp());
        event.setNormalizedEvent(writeJson(normalized));
        event.setStatus("closed");
        event.setOwnerId(action.getOwnerId());
        event.setDeptId(action.getDeptId());
        event.setEventTime(LocalDateTime.now());
        externalEventMapper.insert(event);
    }

    private RepairRecommendation taskRecommendation(SocTicketTask task, SocAsset asset, SocClientRecommendationAction latestAction) {
        String title = firstNotBlank(task.getTaskName(), "需要确认的安全待办");
        String status = task.getStatus();
        return withLatestAction(new RepairRecommendation(
                key("task", task.getId()),
                title,
                "warning",
                firstNotBlank(task.getInstruction(), "安全团队需要你确认当前电脑相关事项。"),
                firstNotBlank(task.getExpectedEvidence(), "按页面要求提交说明，或确认已按安全团队建议处理。"),
                "task",
                task.getAlertId(),
                null,
                task.getTicketId(),
                task.getId(),
                asset.getIp(),
                asset.getHostname(),
                firstNotBlank(status, "pending")
        ), latestAction);
    }

    private RepairRecommendation alertRecommendation(SocAlert alert, SocClientRecommendationAction latestAction) {
        return withLatestAction(new RepairRecommendation(
                key("alert", alert.getId()),
                firstNotBlank(alert.getRuleDescription(), alert.getRuleId(), "安全提醒需要确认"),
                normalizeSeverity(alert.getSeverity()),
                "安全团队发现当前电脑存在需要确认的提醒，可能影响账号、网页访问或主机安全状态。",
                "先不要自行修改系统设置。请查看详情，如果你了解业务背景，可以提交说明；如已按管理员建议处理，可确认已处理。",
                "alert",
                alert.getId(),
                null,
                alert.getTicketId(),
                null,
                alert.getAssetIp(),
                alert.getAssetName(),
                firstNotBlank(alert.getStatus(), "new")
        ), latestAction);
    }

    private RepairRecommendation vulnerabilityRecommendation(SocVulnerability vulnerability, SocClientRecommendationAction latestAction) {
        String title = firstNotBlank(vulnerability.getSoftwareName(), "软件组件") + " 需要修复"
                + (hasText(vulnerability.getCveId()) ? "（" + vulnerability.getCveId() + "）" : "");
        return withLatestAction(new RepairRecommendation(
                key("vulnerability", vulnerability.getId()),
                title,
                normalizeSeverity(vulnerability.getSeverity()),
                "当前电脑或关联业务存在软件组件风险，可能需要安全团队安排升级、补丁或复测。",
                firstNotBlank(vulnerability.getFixSuggestion(), "等待管理员安排升级；如你知道业务影响，请提交说明。"),
                "vulnerability",
                null,
                vulnerability.getId(),
                null,
                null,
                vulnerability.getAssetIp(),
                vulnerability.getAssetName(),
                firstNotBlank(vulnerability.getStatus(), "open")
        ), latestAction);
    }

    private RepairRecommendation withLatestAction(RepairRecommendation recommendation, SocClientRecommendationAction action) {
        if (action == null) {
            return recommendation;
        }
        String status = "confirm".equals(action.getActionType()) ? "confirmed" : "submitted";
        return new RepairRecommendation(recommendation.id(), recommendation.riskTitle(), recommendation.severity(),
                recommendation.impact(), recommendation.recommendedAction(), recommendation.relatedType(),
                recommendation.relatedAlertId(), recommendation.relatedVulnerabilityId(), recommendation.relatedTicketId(),
                recommendation.relatedTaskId(), recommendation.assetIp(), recommendation.assetName(), status);
    }

    private static int recommendationPriority(RepairRecommendation recommendation) {
        int severity = switch (recommendation.severity()) {
            case "critical" -> 0;
            case "high" -> 1;
            case "warning", "medium" -> 2;
            default -> 3;
        };
        int type = switch (recommendation.relatedType()) {
            case "task" -> 0;
            case "alert" -> 1;
            default -> 2;
        };
        return severity * 10 + type;
    }

    static List<CheckupRiskItem> buildRiskItems(SocOperationService.ClientDeviceProfile profile,
                                                RiskScoringService.CalculationInput counts) {
        long openAlerts = profile.alerts().stream().filter(alert -> alert.getStatus() == null || !CLOSED_ALERT_STATUS.contains(alert.getStatus())).count();
        long openVulnerabilities = profile.vulnerabilities().stream().filter(item -> item.getStatus() == null || !CLOSED_VULN_STATUS.contains(item.getStatus())).count();
        long failedBaselines = profile.metrics().failedBaselines();
        long unreviewedFim = profile.metrics().pendingFileIntegrity();
        long localChecks = profile.externalEvents().stream()
                .filter(event -> "terminal".equals(event.getEventType()) || "osquery".equals(event.getSourceType()))
                .count();
        return List.of(
                item("alerts", "安全提醒", severityOf(openAlerts, 10, 1), openAlerts,
                        "当前有 " + openAlerts + " 条未关闭安全提醒。", "优先查看修复建议，必要时联系管理员。"),
                item("vulnerabilities", "软件漏洞", severityOf(openVulnerabilities, 3, 1), openVulnerabilities,
                        "当前有 " + openVulnerabilities + " 个未处理漏洞。", "等待安全团队安排补丁或版本升级。"),
                item("baselines", "配置检查", severityOf(failedBaselines, 3, 1), failedBaselines,
                        "当前有 " + failedBaselines + " 个配置项需要复核。", "按管理员要求补充信息或等待处理。"),
                item("file_changes", "最近变更", severityOf(unreviewedFim, 5, 1), unreviewedFim,
                        "当前有 " + unreviewedFim + " 条最近变更待确认。", "确认是否为本人操作或授权变更。"),
                item("ticket_tasks", "处置任务", severityOf(counts.openPlaybookTasks(), 3, 1), counts.openPlaybookTasks(),
                        "当前有 " + counts.openPlaybookTasks() + " 个处置任务未完成。", "按照我的待办逐项处理。"),
                item("employee_tasks", "我的待办", severityOf(counts.employeePendingTasks(), 3, 1), counts.employeePendingTasks(),
                        "当前有 " + counts.employeePendingTasks() + " 个需要你确认的待办。", "进入我的待办提交说明或确认结果。"),
                item("local_checks", "本机检查记录", localChecks == 0 ? "info" : "low", localChecks,
                        localChecks == 0 ? "暂未发现最近本机检查记录。" : "最近已有 " + localChecks + " 条本机检查记录。",
                        localChecks == 0 ? "如管理员要求，请进入本机检查完成只读检查。" : "继续保留记录供安全团队复核。")
        ).stream().filter(item -> item.count() > 0 || "local_checks".equals(item.itemType())).toList();
    }

    private static CheckupRiskItem item(String type, String name, String severity, long count, String summary, String recommendation) {
        return new CheckupRiskItem(type, name, severity, count, summary, recommendation);
    }

    private static String severityOf(long count, long criticalThreshold, long warningThreshold) {
        if (count >= criticalThreshold) return "critical";
        if (count >= warningThreshold) return "warning";
        return "info";
    }

    private void writeSecurityLog(SocClientCheckup checkup, List<CheckupRiskItem> riskItems) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("checkupId", checkup.getId());
        normalized.put("checkupNo", checkup.getCheckupNo());
        normalized.put("score", checkup.getScore());
        normalized.put("status", checkup.getStatus());
        normalized.put("assetIp", checkup.getAssetIp());
        normalized.put("assetName", checkup.getAssetName());
        normalized.put("riskItems", riskItems.stream().map(item -> Map.of(
                "itemType", item.itemType(),
                "severity", item.severity(),
                "count", item.count()
        )).toList());

        SocExternalEvent event = new SocExternalEvent();
        event.setEventUid("CHECKUP-" + checkup.getId());
        event.setSourceType("client-checkup");
        event.setEventType("security_keeper_checkup");
        event.setSeverity(switch (checkup.getStatus()) {
            case "serious" -> "high";
            case "attention" -> "medium";
            default -> "low";
        });
        event.setRuleId("SECURITY-KEEPER-CHECKUP");
        event.setRuleName("CyberFusion 安全管家体检");
        event.setAssetName(checkup.getAssetName());
        event.setAssetIp(checkup.getAssetIp());
        event.setNormalizedEvent(writeJson(normalized));
        event.setStatus("closed");
        event.setOwnerId(checkup.getOwnerId());
        event.setDeptId(checkup.getDeptId());
        event.setEventTime(checkup.getCheckedAt());
        externalEventMapper.insert(event);
    }

    private CheckupSummary summaryOf(SocClientCheckup checkup) {
        return new CheckupSummary(checkup.getId(), checkup.getCheckupNo(), checkup.getAssetIp(), checkup.getAssetName(),
                checkup.getScore(), checkup.getStatus(), checkup.getSummary(), checkup.getCheckedAt());
    }

    private List<String> recommendations(SocClientCheckup checkup, List<CheckupRiskItem> items) {
        List<String> itemRecommendations = items.stream()
                .filter(item -> !"info".equals(item.severity()))
                .map(CheckupRiskItem::recommendation)
                .filter(this::hasText)
                .distinct()
                .limit(3)
                .toList();
        if (!itemRecommendations.isEmpty()) {
            return itemRecommendations;
        }
        return List.of(firstNotBlank(checkup.getRecommendationSummary(), recommendation(checkup.getStatus())));
    }

    private String summary(String status, String statusReason, SocOperationService.ClientDeviceMetrics metrics) {
        String prefix = switch (status) {
            case "serious" -> "当前电脑存在严重风险，需要优先处理。";
            case "attention" -> "当前电脑需要注意，请按建议完成待办。";
            default -> "当前电脑状态正常，继续保持。";
        };
        return prefix + " " + firstNotBlank(statusReason, metrics.summary(), "");
    }

    private String recommendation(String status) {
        return switch (status) {
            case "serious" -> "优先处理安全提醒和待办，必要时联系管理员。";
            case "attention" -> "查看修复建议并补充安全日志。";
            default -> "保持当前使用习惯，按需提交日志。";
        };
    }

    static String statusFromScore(int score) {
        if (score >= 80) return "serious";
        if (score >= 40) return "attention";
        return "safe";
    }

    private static String key(String type, Long id) {
        return type + "-" + id;
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException("修复建议不存在");
        }
    }

    private String normalizeSeverity(String severity) {
        if (!hasText(severity)) return "info";
        return switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical", "high", "medium", "low" -> severity.toLowerCase(Locale.ROOT);
            default -> "info";
        };
    }

    private LambdaQueryWrapper<SocClientCheckup> scopedCheckupWrapper() {
        LambdaQueryWrapper<SocClientCheckup> wrapper = new LambdaQueryWrapper<SocClientCheckup>()
                .eq(SocClientCheckup::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocClientCheckup::getOwnerId, SocClientCheckup::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocExternalEvent> scopedExternalEventWrapper() {
        LambdaQueryWrapper<SocExternalEvent> wrapper = new LambdaQueryWrapper<SocExternalEvent>()
                .eq(SocExternalEvent::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocExternalEvent::getOwnerId, SocExternalEvent::getDeptId);
        return wrapper;
    }

    private SecurityLogItem eventLogItem(SocExternalEvent event) {
        String sourceType = firstNotBlank(event.getSourceType(), "");
        String eventType = firstNotBlank(event.getEventType(), "");
        if ("client-checkup".equals(sourceType) || "client-remediation".equals(sourceType)) {
            return null;
        }
        if ("client-task".equals(sourceType)) {
            return new SecurityLogItem("event-" + event.getId(), "employee_confirmation",
                    "employee_task_confirm".equals(eventType) ? "已确认一个处理任务" : "已提交一个处理任务说明",
                    firstNotBlank(event.getRuleName(), "员工端待办处理过程已写入安全日志。"),
                    firstNotBlank(event.getStatus(), "closed"), firstNotBlank(event.getSeverity(), "low"),
                    event.getAssetIp(), event.getAssetName(), event.getEventTime());
        }
        if ("terminal".equals(eventType)) {
            return new SecurityLogItem("event-" + event.getId(), "local_check",
                    "已完成一次本机检查",
                    firstNotBlank(event.getRuleName(), "安全团队发布的只读工具已运行完成。"),
                    firstNotBlank(event.getStatus(), "new"), firstNotBlank(event.getSeverity(), "low"),
                    event.getAssetIp(), event.getAssetName(), event.getEventTime());
        }
        if ("host_snapshot".equals(eventType)) {
            return new SecurityLogItem("event-" + event.getId(), "local_check_record",
                    "已生成本机检查记录",
                    firstNotBlank(event.getRuleName(), "本机检查记录已提交给安全团队。"),
                    firstNotBlank(event.getStatus(), "new"), firstNotBlank(event.getSeverity(), "low"),
                    event.getAssetIp(), event.getAssetName(), event.getEventTime());
        }
        if ("osquery".equals(sourceType) || "client_manual_data_report".equals(eventType) || "external_signal".equals(eventType)) {
            return new SecurityLogItem("event-" + event.getId(), "log_submission",
                    "已提交安全日志",
                    firstNotBlank(event.getRuleName(), "员工端已提交一条授权日志记录。"),
                    firstNotBlank(event.getStatus(), "new"), firstNotBlank(event.getSeverity(), "low"),
                    event.getAssetIp(), event.getAssetName(), event.getEventTime());
        }
        return new SecurityLogItem("event-" + event.getId(), "security_event",
                "安全团队记录了一条相关事件",
                firstNotBlank(event.getRuleName(), event.getEventType(), "当前电脑产生一条安全记录。"),
                firstNotBlank(event.getStatus(), "new"), firstNotBlank(event.getSeverity(), "low"),
                event.getAssetIp(), event.getAssetName(), event.getEventTime());
    }

    private String taskStatusTitle(SocTicketTask task) {
        return switch (firstNotBlank(task.getStatus(), "pending")) {
            case "confirmed" -> "已确认一个处理任务";
            case "submitted" -> "已提交一个处理任务说明";
            case "completed" -> "安全团队完成了一个处理任务";
            case "skipped" -> "安全团队跳过了一个处理任务";
            default -> "安全团队创建了处理任务";
        };
    }

    private String employeeStatus(String status) {
        return switch (firstNotBlank(status, "safe")) {
            case "serious" -> "严重";
            case "attention" -> "需要注意";
            default -> "安全";
        };
    }

    private String severityFromStatus(String status) {
        return switch (firstNotBlank(status, "safe")) {
            case "serious" -> "high";
            case "attention" -> "medium";
            default -> "low";
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public record CheckupSummary(Long id, String checkupNo, String assetIp, String assetName,
                                 Integer score, String status, String summary, LocalDateTime checkedAt) {
    }

    public record CheckupRiskItem(String itemType, String itemName, String severity, long count,
                                  String summary, String recommendation) {
    }

    public record CheckupDetail(SocClientCheckup checkup, List<CheckupRiskItem> riskItems,
                                List<String> recommendations) {
    }

    public record RepairRecommendation(String id, String riskTitle, String severity, String impact,
                                       String recommendedAction, String relatedType, Long relatedAlertId,
                                       Long relatedVulnerabilityId, Long relatedTicketId, Long relatedTaskId,
                                       String assetIp, String assetName, String status) {
    }

    public record SecurityLogItem(String id, String type, String title, String description, String status,
                                  String severity, String assetIp, String assetName, LocalDateTime occurredAt) {
    }

    private record RecommendationTarget(RepairRecommendation recommendation, String relatedType, Long relatedId,
                                        String assetIp, String assetName, Long ownerId, Long deptId) {
    }
}
