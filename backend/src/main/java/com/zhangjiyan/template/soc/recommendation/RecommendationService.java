package com.zhangjiyan.template.soc.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.asset.SocAsset;
import com.zhangjiyan.template.soc.asset.SocAssetMapper;
import com.zhangjiyan.template.soc.correlation.SocIncidentEvidence;
import com.zhangjiyan.template.soc.correlation.SocIncidentEvidenceMapper;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.correlation.SocIncidentClusterMapper;
import com.zhangjiyan.template.soc.keeper.SocClientCheckup;
import com.zhangjiyan.template.soc.keeper.SocClientCheckupMapper;
import com.zhangjiyan.template.soc.keeper.SocClientRecommendationAction;
import com.zhangjiyan.template.soc.keeper.SocClientRecommendationActionMapper;
import com.zhangjiyan.template.soc.playbook.SocTicketTask;
import com.zhangjiyan.template.soc.playbook.SocTicketTaskMapper;
import com.zhangjiyan.template.soc.risk.SocAssetRiskFactor;
import com.zhangjiyan.template.soc.risk.SocAssetRiskFactorMapper;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshot;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshotMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerability;
import com.zhangjiyan.template.soc.vulnerability.SocVulnerabilityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final Set<String> CLOSED_INCIDENT_STATUS = Set.of("closed", "ignored");
    private static final Set<String> CLOSED_VULNERABILITY_STATUS = Set.of("fixed", "accepted", "closed");
    private static final Set<String> CLOSED_TICKET_STATUS = Set.of("已关闭", "已归档", "closed", "resolved");
    private static final Set<String> COMPLETED_TASK_STATUS = Set.of("completed", "confirmed", "skipped");
    private static final Set<String> DOWNRANK_ACTION_STATUS = Set.of("confirm", "confirmed", "submitted", "note");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("view", "apply_playbook", "ticket", "confirm", "note");
    private static final Set<String> NON_REAL_SOURCE_TYPES = Set.of("demo", "mock", "local-demo-client", "fixture");

    private final SocIncidentClusterMapper incidentMapper;
    private final SocIncidentEvidenceMapper evidenceMapper;
    private final SocVulnerabilityMapper vulnerabilityMapper;
    private final SocTicketMapper ticketMapper;
    private final SocTicketTaskMapper taskMapper;
    private final SocAlertMapper alertMapper;
    private final SocAssetMapper assetMapper;
    private final SocClientCheckupMapper checkupMapper;
    private final SocAssetRiskSnapshotMapper snapshotMapper;
    private final SocAssetRiskFactorMapper factorMapper;
    private final SocClientRecommendationActionMapper actionMapper;
    private final SocSecurityScope securityScope;

    public List<RecommendationItem> topRecommendations(int limit) {
        List<RecommendationItem> items = new ArrayList<>();
        items.addAll(incidentRecommendations(null));
        items.addAll(vulnerabilityRecommendations(null));
        items.addAll(ticketRecommendations(null));
        items.addAll(taskRecommendations(null, false));
        return sorted(items, limit);
    }

    public List<RecommendationItem> assetRecommendations(Long assetId, int limit) {
        SocAsset asset = assetById(assetId);
        List<RecommendationItem> items = new ArrayList<>();
        items.addAll(riskFactorRecommendations(asset));
        items.addAll(incidentRecommendations(asset));
        items.addAll(vulnerabilityRecommendations(asset));
        items.addAll(ticketRecommendations(asset));
        items.addAll(taskRecommendations(asset, false));
        items.addAll(checkupRecommendations(asset));
        return sorted(items, limit);
    }

    public List<ClientNextAction> clientNextActions(String assetIp, int limit) {
        SocAsset asset = assetByIp(assetIp);
        List<RecommendationItem> items = new ArrayList<>();
        items.addAll(taskRecommendations(asset, true));
        items.addAll(checkupRecommendations(asset));
        items.addAll(vulnerabilityRecommendations(asset));
        items.addAll(incidentRecommendations(asset));
        items.addAll(riskFactorRecommendations(asset));
        return sorted(items, limit).stream()
                .map(this::toClientAction)
                .toList();
    }

    @Transactional
    public RecommendationActionRecord recordAction(String key, RecommendationActionRequest request) {
        String actionType = normalizeAction(request == null ? null : request.actionType());
        RecommendationTarget target = resolveTarget(key, request);
        SocClientRecommendationAction action = new SocClientRecommendationAction();
        action.setRecommendationKey(key);
        action.setActionType(actionType);
        action.setNote(limit(firstNotBlank(request == null ? null : request.note(), actionNote(actionType)), 500));
        action.setRelatedType(target.relatedBizType());
        action.setRelatedId(target.relatedBizId());
        action.setAssetIp(firstNotBlank(target.assetIp(), "-"));
        action.setAssetName(target.assetName());
        action.setOwnerId(firstNonNull(target.ownerId(), securityScope.currentUserId()));
        action.setDeptId(firstNonNull(target.deptId(), securityScope.currentDeptId()));
        action.setOperatorName(securityScope.currentUsername());
        action.setDeleted(0);
        actionMapper.insert(action);
        return new RecommendationActionRecord(action.getId(), key, actionType, target.relatedBizType(), target.relatedBizId(), action.getCreatedAt());
    }

    private List<RecommendationItem> incidentRecommendations(SocAsset asset) {
        LambdaQueryWrapper<SocIncidentCluster> wrapper = scopedIncidentWrapper(asset)
                .orderByDesc(SocIncidentCluster::getScore)
                .orderByDesc(SocIncidentCluster::getUpdatedAt)
                .last("LIMIT 80");
        List<SocIncidentCluster> incidents = incidentMapper.selectList(wrapper);
        Map<Long, List<SocIncidentEvidence>> evidenceByClusterId = validEvidenceByClusterIds(incidents.stream()
                .map(SocIncidentCluster::getId)
                .filter(Objects::nonNull)
                .toList());
        return incidents.stream()
                .filter(incident -> hasTraceableIncidentLineage(incident, evidenceByClusterId.get(incident.getId())))
                .map(incident -> {
                    boolean open = !CLOSED_INCIDENT_STATUS.contains(normalize(incident.getStatus()));
                    int score = severityScore(incident.getSeverity()) + nz(incident.getScore()) / 4 + nz(incident.getEvidenceCount());
                    if (!open) score -= 70;
                    if (incident.getTicketId() != null) score -= 15;
                    String key = key("incident", incident.getId());
                    return item(key, "优先处理事件簇：" + firstNotBlank(incident.getTitle(), incident.getClusterNo()),
                            score, "同一资产存在多源证据关联，证据数 " + nz(incident.getEvidenceCount()) + "，当前状态：" + firstNotBlank(incident.getStatus(), "open") + "。",
                            firstNotBlank(incident.getRecommendation(), incident.getTicketId() == null ? "查看事件链并转为工单。" : "查看已关联工单并补齐处置结论。"),
                            "incident", incident.getId(), "analyst", firstNotBlank(incident.getStatus(), "open"),
                            incident.getAssetId(), firstNotBlank(incident.getAssetIp(), incident.getPrimaryAssetIp()), firstNotBlank(incident.getHostname(), incident.getPrimaryHostname()),
                            score);
                })
                .toList();
    }

    private List<RecommendationItem> vulnerabilityRecommendations(SocAsset asset) {
        LambdaQueryWrapper<SocVulnerability> wrapper = scopedVulnerabilityWrapper(asset)
                .orderByDesc(SocVulnerability::getDetectedAt)
                .last("LIMIT 80");
        return vulnerabilityMapper.selectList(wrapper).stream()
                .filter(vulnerability -> isRealSourceType(vulnerability.getSourceType()))
                .filter(vulnerability -> asset != null || linkedAssetExists(vulnerability.getAssetIp(), vulnerability.getAssetName()))
                .map(vulnerability -> {
                    boolean open = !CLOSED_VULNERABILITY_STATUS.contains(normalize(vulnerability.getStatus()));
                    int score = severityScore(vulnerability.getSeverity()) - (open ? 0 : 55);
                    String title = firstNotBlank(vulnerability.getSoftwareName(), "软件组件") + " 存在" + severityLabel(vulnerability.getSeverity()) + "漏洞"
                            + (hasText(vulnerability.getCveId()) ? "（" + vulnerability.getCveId() + "）" : "");
                    return item(key("vulnerability", vulnerability.getId()), title, score,
                            "漏洞状态：" + firstNotBlank(vulnerability.getStatus(), "open") + "，影响资产：" + firstNotBlank(vulnerability.getAssetName(), vulnerability.getAssetIp(), "-") + "。",
                            firstNotBlank(vulnerability.getFixSuggestion(), "确认影响范围，安排补丁升级并复测。"),
                            "vulnerability", vulnerability.getId(), "analyst", firstNotBlank(vulnerability.getStatus(), "open"),
                            null, vulnerability.getAssetIp(), vulnerability.getAssetName(), score);
                })
                .toList();
    }

    private List<RecommendationItem> ticketRecommendations(SocAsset asset) {
        Map<Long, SocAlert> alerts = alertsById(null);
        LambdaQueryWrapper<SocTicket> wrapper = scopedTicketWrapper()
                .orderByDesc(SocTicket::getDueAt)
                .orderByDesc(SocTicket::getUpdatedAt)
                .last("LIMIT 100");
        List<SocTicket> tickets = ticketMapper.selectList(wrapper);
        if (asset != null) {
            Set<Long> assetAlertIds = alertIdsForAsset(asset);
            tickets = tickets.stream().filter(ticket -> ticket.getAlertId() != null && assetAlertIds.contains(ticket.getAlertId())).toList();
        }
        LocalDateTime now = LocalDateTime.now();
        return tickets.stream()
                .filter(ticket -> hasTraceableTicketLineage(ticket, alerts.get(ticket.getAlertId())))
                .map(ticket -> {
            boolean closed = CLOSED_TICKET_STATUS.contains(normalize(ticket.getStatus()));
            boolean overdue = ticket.getDueAt() != null && ticket.getDueAt().isBefore(now) && !closed;
            SocAlert alert = alerts.get(ticket.getAlertId());
            int score = overdue ? 86 : 50;
            if (closed) score -= 60;
            return item(key("ticket", ticket.getId()), overdue ? "推动超时工单：" + ticket.getTicketNo() : "跟进工单：" + ticket.getTicketNo(),
                    score, overdue ? "工单已超过计划时间但仍未关闭。" : "工单仍在处置队列中，需要更新进展或关闭结论。",
                    closed ? "保留关闭结论，必要时纳入报告。" : "查看工单时间线，补充处置进展或复核阻塞原因。",
                    "ticket", ticket.getId(), "analyst", firstNotBlank(ticket.getStatus(), "open"),
                    null, alert == null ? null : alert.getAssetIp(), alert == null ? null : alert.getAssetName(), score);
        }).toList();
    }

    private List<RecommendationItem> taskRecommendations(SocAsset asset, boolean currentEmployeeOnly) {
        LambdaQueryWrapper<SocTicketTask> wrapper = new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getDeleted, 0)
                .orderByAsc(SocTicketTask::getSortOrder)
                .orderByDesc(SocTicketTask::getUpdatedAt)
                .last("LIMIT 120");
        if (currentEmployeeOnly) {
            wrapper.eq(SocTicketTask::getAssigneeType, "employee")
                    .eq(SocTicketTask::getAssigneeId, securityScope.currentUserId());
        }
        List<SocTicketTask> tasks = taskMapper.selectList(wrapper);
        Map<Long, SocTicket> tickets = ticketsById(tasks.stream().map(SocTicketTask::getTicketId).filter(Objects::nonNull).toList());
        if (asset != null) {
            Set<Long> assetAlertIds = alertIdsForAsset(asset);
            tasks = tasks.stream().filter(task -> {
                SocTicket ticket = tickets.get(task.getTicketId());
                Long alertId = firstNonNull(task.getAlertId(), ticket == null ? null : ticket.getAlertId());
                return alertId != null && assetAlertIds.contains(alertId);
            }).toList();
        } else if (!securityScope.canViewAllData()) {
            tasks = tasks.stream().filter(task -> {
                SocTicket ticket = tickets.get(task.getTicketId());
                return ticket != null && securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId());
            }).toList();
        }
        Set<Long> alertIds = tasks.stream()
                .map(task -> {
                    SocTicket ticket = task.getTicketId() == null ? null : tickets.get(task.getTicketId());
                    return firstNonNull(task.getAlertId(), ticket == null ? null : ticket.getAlertId());
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SocAlert> alerts = alertsById(alertIds);
        return tasks.stream()
                .filter(task -> {
                    SocTicket ticket = task.getTicketId() == null ? null : tickets.get(task.getTicketId());
                    SocAlert alert = alerts.get(firstNonNull(task.getAlertId(), ticket == null ? null : ticket.getAlertId()));
                    return hasTraceableTaskLineage(task, ticket, alert);
                })
                .map(task -> {
            boolean done = COMPLETED_TASK_STATUS.contains(normalize(task.getStatus()));
            boolean employee = "employee".equals(task.getAssigneeType());
            int score = (employee ? 82 : 66) - (done ? 52 : 0);
            SocTicket ticket = task.getTicketId() == null ? null : tickets.get(task.getTicketId());
            SocAlert alert = alerts.get(firstNonNull(task.getAlertId(), ticket == null ? null : ticket.getAlertId()));
            String type = employee ? "client_task" : "playbook_task";
            return item(key(type, task.getId()), employee ? "跟进员工待办：" + firstNotBlank(task.getTaskName(), task.getTaskKey()) : "完成剧本任务：" + firstNotBlank(task.getTaskName(), task.getTaskKey()),
                    score, done ? "任务已完成或确认，作为闭环证据保留。" : "处置剧本中仍有未完成任务，当前状态：" + firstNotBlank(task.getStatus(), "pending") + "。",
                    employee ? "提醒员工提交说明、日志或本机检查记录。" : "进入工单任务清单，完成复核、验证或报告记录。",
                    type, task.getId(), employee ? "employee" : "analyst", firstNotBlank(task.getStatus(), "pending"),
                    null, alert == null ? null : alert.getAssetIp(), alert == null ? null : alert.getAssetName(), score);
        }).toList();
    }

    private List<RecommendationItem> checkupRecommendations(SocAsset asset) {
        SocClientCheckup checkup = checkupMapper.selectOne(new LambdaQueryWrapper<SocClientCheckup>()
                .eq(SocClientCheckup::getDeleted, 0)
                .and(w -> w.eq(SocClientCheckup::getAssetId, asset.getId()).or().eq(SocClientCheckup::getAssetIp, asset.getIp()))
                .orderByDesc(SocClientCheckup::getCheckedAt)
                .last("LIMIT 1"));
        if (checkup == null) {
            return List.of(item("client-checkup-missing-" + asset.getId(), "请先完成本机检查", 58,
                    "当前资产暂无最近一键体检记录。", "引导员工进入安全管家完成一键体检或本机只读检查。",
                    "client_checkup", asset.getId(), "employee", "pending", asset.getId(), asset.getIp(), asset.getHostname(), 58));
        }
        String status = normalize(checkup.getStatus());
        if (!Set.of("attention", "warning", "serious", "critical").contains(status)) {
            return List.of();
        }
        int score = Set.of("serious", "critical").contains(status) ? 74 : 54;
        return List.of(item(key("client_checkup", checkup.getId()), "复核员工体检结果：" + employeeStatus(checkup.getStatus()), score,
                firstNotBlank(checkup.getSummary(), "员工端一键体检提示需要关注。"),
                firstNotBlank(checkup.getRecommendationSummary(), "请员工查看修复建议、提交安全日志或完成本机检查。"),
                "client_checkup", checkup.getId(), "employee", firstNotBlank(checkup.getStatus(), "attention"),
                asset.getId(), asset.getIp(), asset.getHostname(), score));
    }

    private List<RecommendationItem> riskFactorRecommendations(SocAsset asset) {
        SocAssetRiskSnapshot snapshot = snapshotMapper.selectOne(new LambdaQueryWrapper<SocAssetRiskSnapshot>()
                .eq(SocAssetRiskSnapshot::getAssetId, asset.getId())
                .orderByDesc(SocAssetRiskSnapshot::getCalculatedAt)
                .last("LIMIT 1"));
        if (snapshot == null) {
            return List.of();
        }
        return factorMapper.selectList(new LambdaQueryWrapper<SocAssetRiskFactor>()
                        .eq(SocAssetRiskFactor::getSnapshotId, snapshot.getId())
                        .gt(SocAssetRiskFactor::getFactorScore, 0)
                        .orderByDesc(SocAssetRiskFactor::getFactorScore)
                        .last("LIMIT 8"))
                .stream()
                .map(factor -> item("risk-factor-" + asset.getId() + "-" + factor.getFactorType() + "-" + nz(factor.getRelatedBizId()),
                        factor.getFactorName(), 38 + nz(factor.getFactorScore()), factor.getExplanation(),
                        firstNotBlank(factor.getRecommendation(), "按风险画像建议完成处置。"),
                        firstNotBlank(factor.getRelatedBizType(), "risk_factor"), factor.getRelatedBizId(), assigneeFor(factor.getRelatedBizType()),
                        "open", asset.getId(), asset.getIp(), asset.getHostname(), 38 + nz(factor.getFactorScore())))
                .toList();
    }

    private RecommendationTarget resolveTarget(String key, RecommendationActionRequest request) {
        KeyParts parts = parseKey(key);
        return switch (parts.type()) {
            case "incident" -> incidentTarget(parts.id());
            case "vulnerability" -> vulnerabilityTarget(parts.id());
            case "ticket" -> ticketTarget(parts.id());
            case "playbook_task", "client_task" -> taskTarget(parts.id(), parts.type());
            case "client_checkup" -> checkupTarget(parts.id());
            default -> requestTarget(parts.type(), parts.id(), request);
        };
    }

    private RecommendationTarget incidentTarget(Long id) {
        SocIncidentCluster incident = incidentMapper.selectById(id);
        if (incident == null || Objects.equals(incident.getDeleted(), 1) || !securityScope.canAccess(incident.getOwnerId(), incident.getDeptId())) {
            throw new BusinessException("无权记录该推荐动作");
        }
        return new RecommendationTarget("incident", incident.getId(), firstNotBlank(incident.getAssetIp(), incident.getPrimaryAssetIp()),
                firstNotBlank(incident.getHostname(), incident.getPrimaryHostname()), incident.getOwnerId(), incident.getDeptId());
    }

    private RecommendationTarget vulnerabilityTarget(Long id) {
        SocVulnerability vulnerability = vulnerabilityMapper.selectById(id);
        if (vulnerability == null || Objects.equals(vulnerability.getDeleted(), 1) || !securityScope.canAccess(vulnerability.getOwnerId(), vulnerability.getDeptId())) {
            throw new BusinessException("无权记录该推荐动作");
        }
        return new RecommendationTarget("vulnerability", vulnerability.getId(), vulnerability.getAssetIp(), vulnerability.getAssetName(), vulnerability.getOwnerId(), vulnerability.getDeptId());
    }

    private RecommendationTarget ticketTarget(Long id) {
        SocTicket ticket = ticketMapper.selectById(id);
        if (ticket == null || Objects.equals(ticket.getDeleted(), 1) || !securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId())) {
            throw new BusinessException("无权记录该推荐动作");
        }
        SocAlert alert = ticket.getAlertId() == null ? null : alertMapper.selectById(ticket.getAlertId());
        return new RecommendationTarget("ticket", ticket.getId(), alert == null ? null : alert.getAssetIp(), alert == null ? null : alert.getAssetName(), ticket.getAssigneeId(), ticket.getDeptId());
    }

    private RecommendationTarget taskTarget(Long id, String type) {
        SocTicketTask task = taskMapper.selectById(id);
        if (task == null || Objects.equals(task.getDeleted(), 1)) {
            throw new BusinessException("无权记录该推荐动作");
        }
        if ("employee".equals(task.getAssigneeType()) && !Objects.equals(task.getAssigneeId(), securityScope.currentUserId()) && !securityScope.canViewAllData()) {
            throw new BusinessException("无权记录该推荐动作");
        }
        SocTicket ticket = task.getTicketId() == null ? null : ticketMapper.selectById(task.getTicketId());
        if (!"employee".equals(task.getAssigneeType()) && ticket != null && !securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId())) {
            throw new BusinessException("无权记录该推荐动作");
        }
        SocAlert alert = task.getAlertId() == null ? null : alertMapper.selectById(task.getAlertId());
        return new RecommendationTarget(type, task.getId(), alert == null ? null : alert.getAssetIp(), alert == null ? null : alert.getAssetName(), task.getAssigneeId(), ticket == null ? null : ticket.getDeptId());
    }

    private RecommendationTarget checkupTarget(Long id) {
        SocClientCheckup checkup = checkupMapper.selectById(id);
        if (checkup == null || Objects.equals(checkup.getDeleted(), 1) || !securityScope.canAccess(checkup.getOwnerId(), checkup.getDeptId())) {
            throw new BusinessException("无权记录该推荐动作");
        }
        return new RecommendationTarget("client_checkup", checkup.getId(), checkup.getAssetIp(), checkup.getAssetName(), checkup.getOwnerId(), checkup.getDeptId());
    }

    private RecommendationTarget requestTarget(String type, Long id, RecommendationActionRequest request) {
        if (request == null || !hasText(request.relatedBizType()) || request.relatedBizId() == null) {
            throw new BusinessException("推荐动作目标不完整");
        }
        return new RecommendationTarget(request.relatedBizType(), request.relatedBizId(), request.assetIp(), request.assetName(), securityScope.currentUserId(), securityScope.currentDeptId());
    }

    private SocAsset assetById(Long assetId) {
        SocAsset asset = assetMapper.selectById(assetId);
        if (asset == null || Objects.equals(asset.getDeleted(), 1) || !securityScope.canAccess(asset.getOwnerId(), asset.getDeptId())) {
            throw new BusinessException("资产不存在或无权访问");
        }
        return asset;
    }

    private SocAsset assetByIp(String assetIp) {
        SocAsset asset = assetMapper.selectOne(scopedAssetWrapper()
                .eq(SocAsset::getIp, assetIp)
                .last("LIMIT 1"));
        if (asset == null) {
            throw new BusinessException("当前账号无权查看该电脑推荐动作");
        }
        return asset;
    }

    private LambdaQueryWrapper<SocAsset> scopedAssetWrapper() {
        LambdaQueryWrapper<SocAsset> wrapper = new LambdaQueryWrapper<SocAsset>().eq(SocAsset::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocAsset::getOwnerId, SocAsset::getDeptId);
        return wrapper;
    }

    private LambdaQueryWrapper<SocIncidentCluster> scopedIncidentWrapper(SocAsset asset) {
        LambdaQueryWrapper<SocIncidentCluster> wrapper = new LambdaQueryWrapper<SocIncidentCluster>().eq(SocIncidentCluster::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocIncidentCluster::getOwnerId, SocIncidentCluster::getDeptId);
        if (asset != null) {
            wrapper.and(w -> w.eq(SocIncidentCluster::getAssetIp, asset.getIp())
                    .or().eq(SocIncidentCluster::getPrimaryAssetIp, asset.getIp())
                    .or().eq(SocIncidentCluster::getHostname, asset.getHostname())
                    .or().eq(SocIncidentCluster::getPrimaryHostname, asset.getHostname()));
        }
        return wrapper;
    }

    private LambdaQueryWrapper<SocVulnerability> scopedVulnerabilityWrapper(SocAsset asset) {
        LambdaQueryWrapper<SocVulnerability> wrapper = new LambdaQueryWrapper<SocVulnerability>().eq(SocVulnerability::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocVulnerability::getOwnerId, SocVulnerability::getDeptId);
        if (asset != null) {
            wrapper.and(w -> w.eq(SocVulnerability::getAssetIp, asset.getIp()).or().eq(SocVulnerability::getAssetName, asset.getHostname()));
        }
        return wrapper;
    }

    private LambdaQueryWrapper<SocTicket> scopedTicketWrapper() {
        LambdaQueryWrapper<SocTicket> wrapper = new LambdaQueryWrapper<SocTicket>().eq(SocTicket::getDeleted, 0);
        securityScope.applyDataScope(wrapper, SocTicket::getAssigneeId, SocTicket::getDeptId);
        return wrapper;
    }

    private Set<Long> alertIdsForAsset(SocAsset asset) {
        return alertMapper.selectList(new LambdaQueryWrapper<SocAlert>()
                        .eq(SocAlert::getDeleted, 0)
                        .and(w -> w.eq(SocAlert::getAssetIp, asset.getIp()).or().eq(SocAlert::getAssetName, asset.getHostname())))
                .stream()
                .map(SocAlert::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, SocAlert> alertsById(Collection<Long> ids) {
        LambdaQueryWrapper<SocAlert> wrapper = new LambdaQueryWrapper<SocAlert>().eq(SocAlert::getDeleted, 0);
        if (ids != null && !ids.isEmpty()) {
            wrapper.in(SocAlert::getId, ids);
        } else {
            wrapper.last("LIMIT 200");
        }
        return alertMapper.selectList(wrapper).stream()
                .filter(alert -> alert.getId() != null)
                .collect(java.util.stream.Collectors.toMap(SocAlert::getId, alert -> alert, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, SocTicket> ticketsById(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return ticketMapper.selectList(new LambdaQueryWrapper<SocTicket>().in(SocTicket::getId, ids).eq(SocTicket::getDeleted, 0)).stream()
                .filter(ticket -> ticket.getId() != null)
                .collect(java.util.stream.Collectors.toMap(SocTicket::getId, ticket -> ticket, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, List<SocIncidentEvidence>> validEvidenceByClusterIds(Collection<Long> clusterIds) {
        if (clusterIds == null || clusterIds.isEmpty()) {
            return Map.of();
        }
        return evidenceMapper.selectList(new LambdaQueryWrapper<SocIncidentEvidence>()
                        .eq(SocIncidentEvidence::getDeleted, 0)
                        .in(SocIncidentEvidence::getClusterId, clusterIds))
                .stream()
                .filter(this::isTraceableEvidence)
                .collect(java.util.stream.Collectors.groupingBy(SocIncidentEvidence::getClusterId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    private boolean hasTraceableIncidentLineage(SocIncidentCluster incident, List<SocIncidentEvidence> evidence) {
        return incident != null
                && incident.getId() != null
                && nz(incident.getEvidenceCount()) > 0
                && hasRealSourceList(incident.getSourceTypes())
                && evidence != null
                && !evidence.isEmpty();
    }

    private boolean hasTraceableTicketLineage(SocTicket ticket, SocAlert alert) {
        return ticket != null
                && ticket.getId() != null
                && ticket.getAlertId() != null
                && alert != null
                && isTraceableAlert(alert);
    }

    private boolean hasTraceableTaskLineage(SocTicketTask task, SocTicket ticket, SocAlert alert) {
        return task != null
                && task.getId() != null
                && task.getTicketId() != null
                && ticket != null
                && firstNonNull(task.getAlertId(), ticket.getAlertId()) != null
                && isTraceableAlert(alert);
    }

    private boolean isTraceableEvidence(SocIncidentEvidence evidence) {
        return evidence != null
                && !Objects.equals(evidence.getDeleted(), 1)
                && evidence.getClusterId() != null
                && evidence.getEvidenceId() != null
                && hasText(evidence.getEvidenceType())
                && isRealSourceType(evidence.getSourceType())
                && hasText(firstNotBlank(evidence.getAssetIp(), evidence.getHostname(), evidence.getEvidenceUid()));
    }

    private boolean isTraceableAlert(SocAlert alert) {
        return alert != null
                && alert.getId() != null
                && !Objects.equals(alert.getDeleted(), 1)
                && isRealSourceType(alert.getSourceType())
                && hasText(firstNotBlank(alert.getAssetIp(), alert.getAssetName()))
                && hasText(firstNotBlank(alert.getAlertUid(), alert.getRawRef(), alert.getBatchId()));
    }

    private boolean linkedAssetExists(String assetIp, String assetName) {
        if (!hasText(assetIp) && !hasText(assetName)) {
            return false;
        }
        Long count = assetMapper.selectCount(scopedAssetWrapper()
                .and(w -> w.eq(hasText(assetIp), SocAsset::getIp, assetIp)
                        .or()
                        .eq(hasText(assetName), SocAsset::getHostname, assetName)));
        return count != null && count > 0;
    }

    private boolean hasRealSourceList(String sourceTypes) {
        if (!hasText(sourceTypes)) {
            return false;
        }
        return Arrays.stream(sourceTypes.split(","))
                .map(RecommendationService::normalize)
                .filter(RecommendationService::hasText)
                .anyMatch(source -> !NON_REAL_SOURCE_TYPES.contains(source));
    }

    private boolean isRealSourceType(String sourceType) {
        String normalized = normalize(sourceType);
        return hasText(normalized) && !NON_REAL_SOURCE_TYPES.contains(normalized);
    }

    private List<RecommendationItem> sorted(List<RecommendationItem> items, int limit) {
        Map<String, SocClientRecommendationAction> actions = latestActions(items.stream().map(RecommendationItem::key).toList());
        return items.stream()
                .map(item -> withAction(item, actions.get(item.key())))
                .sorted(Comparator.comparing(RecommendationItem::priorityScore).reversed()
                        .thenComparing(RecommendationItem::title))
                .limit(Math.max(1, limit))
                .toList();
    }

    private Map<String, SocClientRecommendationAction> latestActions(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return Map.of();
        Map<String, SocClientRecommendationAction> result = new LinkedHashMap<>();
        List<SocClientRecommendationAction> actions = actionMapper.selectList(new LambdaQueryWrapper<SocClientRecommendationAction>()
                .eq(SocClientRecommendationAction::getDeleted, 0)
                .in(SocClientRecommendationAction::getRecommendationKey, keys)
                .orderByDesc(SocClientRecommendationAction::getCreatedAt)
                .last("LIMIT 300"));
        for (SocClientRecommendationAction action : actions) {
            if (securityScope.canAccess(action.getOwnerId(), action.getDeptId())) {
                result.putIfAbsent(action.getRecommendationKey(), action);
            }
        }
        return result;
    }

    private RecommendationItem withAction(RecommendationItem item, SocClientRecommendationAction action) {
        if (action == null) return item;
        int reducedScore = DOWNRANK_ACTION_STATUS.contains(normalize(action.getActionType()))
                ? Math.max(1, item.priorityScore() - 25)
                : item.priorityScore();
        String status = "confirm".equals(action.getActionType()) ? "confirmed" : firstNotBlank(action.getActionType(), item.status());
        return new RecommendationItem(item.key(), item.title(), priority(reducedScore), item.reason(), item.recommendedAction(),
                item.relatedBizType(), item.relatedBizId(), item.assigneeType(), status, item.assetId(), item.assetIp(),
                item.assetName(), reducedScore);
    }

    private RecommendationItem item(String key, String title, int priorityScore, String reason, String recommendedAction,
                                    String relatedBizType, Long relatedBizId, String assigneeType, String status,
                                    Long assetId, String assetIp, String assetName, int rawScore) {
        int score = Math.max(1, Math.min(120, priorityScore));
        return new RecommendationItem(key, title, priority(score), reason, recommendedAction, relatedBizType,
                relatedBizId == null ? 0L : relatedBizId, firstNotBlank(assigneeType, "analyst"),
                firstNotBlank(status, "open"), assetId, assetIp, assetName, score);
    }

    private ClientNextAction toClientAction(RecommendationItem item) {
        String title = switch (item.relatedBizType()) {
            case "client_task" -> "请确认安全团队分配的待办";
            case "client_checkup" -> "请先完成本机检查";
            case "vulnerability" -> "请查看修复建议";
            case "incident" -> "请配合安全团队处理风险";
            default -> "请查看安全建议";
        };
        String action = switch (item.relatedBizType()) {
            case "client_task" -> "进入我的待办，提交说明或确认处理结果。";
            case "client_checkup" -> "点击一键体检，或进入本机检查完成只读检查。";
            case "vulnerability" -> "查看风险修复建议，等待管理员安排升级或复测。";
            case "incident" -> "按安全团队要求提交日志、说明或本机检查记录。";
            default -> item.recommendedAction();
        };
        return new ClientNextAction(item.key(), title, item.priority(), item.reason(), action, item.relatedBizType(), item.relatedBizId(), item.status());
    }

    private KeyParts parseKey(String key) {
        if (!hasText(key)) throw new BusinessException("推荐动作不存在");
        String normalized = key.trim();
        for (String prefix : List.of("risk-factor", "client-checkup-missing")) {
            if (normalized.startsWith(prefix + "-")) {
                return new KeyParts(prefix, 0L);
            }
        }
        int split = normalized.lastIndexOf('-');
        if (split <= 0 || split == normalized.length() - 1) {
            throw new BusinessException("推荐动作不存在");
        }
        try {
            return new KeyParts(normalized.substring(0, split), Long.parseLong(normalized.substring(split + 1)));
        } catch (NumberFormatException ex) {
            throw new BusinessException("推荐动作不存在");
        }
    }

    private String normalizeAction(String value) {
        String normalized = normalize(value);
        if (!ALLOWED_ACTIONS.contains(normalized)) {
            return "view";
        }
        return normalized;
    }

    private String actionNote(String actionType) {
        return switch (actionType) {
            case "apply_playbook" -> "采纳推荐并准备应用处置剧本";
            case "ticket" -> "采纳推荐并准备转入工单";
            case "confirm" -> "采纳推荐并确认已处理";
            case "note" -> "采纳推荐并提交说明";
            default -> "查看推荐动作";
        };
    }

    private String assigneeFor(String relatedBizType) {
        return "client_task".equals(relatedBizType) || "client_checkup".equals(relatedBizType) ? "employee" : "analyst";
    }

    private static String key(String type, Long id) {
        return type + "-" + (id == null ? 0 : id);
    }

    private String priority(int score) {
        if (score >= 90) return "critical";
        if (score >= 70) return "high";
        if (score >= 40) return "medium";
        return "low";
    }

    private int severityScore(String severity) {
        return switch (normalize(severity)) {
            case "critical", "严重" -> 95;
            case "high", "高危" -> 78;
            case "medium", "中危" -> 52;
            case "low", "低危" -> 28;
            default -> 20;
        };
    }

    private String severityLabel(String severity) {
        return switch (normalize(severity)) {
            case "critical", "严重" -> "严重";
            case "high", "高危" -> "高危";
            case "medium", "中危" -> "中危";
            case "low", "低危" -> "低危";
            default -> "待确认";
        };
    }

    private String employeeStatus(String status) {
        return switch (normalize(status)) {
            case "serious", "critical" -> "严重风险";
            case "attention", "warning" -> "需要注意";
            default -> "安全";
        };
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static String limit(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return value.substring(0, limit);
    }

    private record KeyParts(String type, Long id) {
    }

    private record RecommendationTarget(String relatedBizType, Long relatedBizId, String assetIp, String assetName,
                                        Long ownerId, Long deptId) {
    }

    public record RecommendationItem(String key, String title, String priority, String reason, String recommendedAction,
                                     String relatedBizType, Long relatedBizId, String assigneeType, String status,
                                     Long assetId, String assetIp, String assetName, int priorityScore) {
    }

    public record ClientNextAction(String key, String title, String priority, String reason, String recommendedAction,
                                   String relatedBizType, Long relatedBizId, String status) {
    }

    public record RecommendationActionRecord(Long id, String recommendationKey, String actionType, String relatedBizType,
                                             Long relatedBizId, LocalDateTime createdAt) {
    }
}
