package com.zhangjiyan.template.soc.playbook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.SocSecurityScope;
import com.zhangjiyan.template.soc.alert.AlertActionRequest;
import com.zhangjiyan.template.soc.alert.SocAlert;
import com.zhangjiyan.template.soc.alert.SocAlertMapper;
import com.zhangjiyan.template.soc.external.SocExternalEvent;
import com.zhangjiyan.template.soc.external.SocExternalEventMapper;
import com.zhangjiyan.template.soc.ticket.SocTicket;
import com.zhangjiyan.template.soc.ticket.SocTicketMapper;
import com.zhangjiyan.template.soc.ticket.SocTicketTimeline;
import com.zhangjiyan.template.soc.ticket.SocTicketTimelineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ResponsePlaybookService {

    private final SocResponsePlaybookMapper playbookMapper;
    private final SocResponsePlaybookStepMapper stepMapper;
    private final SocTicketTaskMapper taskMapper;
    private final SocPlaybookMatchLogMapper matchLogMapper;
    private final SocTicketMapper ticketMapper;
    private final SocTicketTimelineMapper timelineMapper;
    private final SocAlertMapper alertMapper;
    private final SocExternalEventMapper externalEventMapper;
    private final SocOperationService socOperationService;
    private final SocSecurityScope securityScope;

    private static final Pattern FORBIDDEN_TEXT = Pattern.compile(
            "(?i)(bash\\s+-c|sh\\s+-c|cmd\\s+/c|powershell|python\\s+-c|node\\s+-e|perl\\s+-e|ruby\\s+-e|curl\\s+|wget\\s+|nmap|sqlmap|metasploit|msfconsole|nc\\s+|netcat|payload|exploit|反弹|自动修复|rm\\s+|del\\s+|chmod\\s+|chown\\s+|sudo\\s+|su\\s+)"
    );
    private static final List<String> FORBIDDEN_METACHARS = List.of(";", "|", "&&", "||", "`", "$(", "${", "\n", "\r");

    public PageResult<SocResponsePlaybook> page(long pageNum, long pageSize, String sourceType, String status, String keyword) {
        LambdaQueryWrapper<SocResponsePlaybook> wrapper = new LambdaQueryWrapper<SocResponsePlaybook>()
                .eq(SocResponsePlaybook::getDeleted, 0)
                .eq(notBlank(sourceType), SocResponsePlaybook::getSourceType, sourceType)
                .eq(notBlank(status), SocResponsePlaybook::getStatus, status)
                .and(notBlank(keyword), w -> w.like(SocResponsePlaybook::getPlaybookKey, keyword)
                        .or().like(SocResponsePlaybook::getPlaybookName, keyword)
                        .or().like(SocResponsePlaybook::getDescription, keyword))
                .orderByAsc(SocResponsePlaybook::getSortOrder)
                .orderByDesc(SocResponsePlaybook::getUpdatedAt);
        return PageResult.from(playbookMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public PlaybookDetail detail(Long id) {
        SocResponsePlaybook playbook = existingPlaybook(id);
        return new PlaybookDetail(playbook, stepsOf(id));
    }

    @Transactional
    public PlaybookDetail create(PlaybookRequest request) {
        validateRequest(request);
        SocResponsePlaybook playbook = new SocResponsePlaybook();
        applyRequest(playbook, request);
        playbook.setVersion(1);
        playbook.setCreatedBy(securityScope.currentUserId());
        playbook.setUpdatedBy(securityScope.currentUserId());
        playbook.setDeleted(0);
        playbookMapper.insert(playbook);
        replaceSteps(playbook.getId(), request.steps());
        return detail(playbook.getId());
    }

    @Transactional
    public PlaybookDetail update(Long id, PlaybookRequest request) {
        validateRequest(request);
        SocResponsePlaybook playbook = existingPlaybook(id);
        applyRequest(playbook, request);
        playbook.setVersion((playbook.getVersion() == null ? 1 : playbook.getVersion()) + 1);
        playbook.setUpdatedBy(securityScope.currentUserId());
        playbookMapper.updateById(playbook);
        replaceSteps(playbook.getId(), request.steps());
        return detail(playbook.getId());
    }

    public ValidationResult validateExisting(Long id) {
        SocResponsePlaybook playbook = existingPlaybook(id);
        validateSafeText(playbook.getPlaybookName(), "playbookName");
        validateSafeText(playbook.getDescription(), "description");
        validateSafeText(playbook.getMatchExpression(), "matchExpression");
        for (SocResponsePlaybookStep step : stepsOf(id)) {
            validateSafeText(step.getStepName(), "stepName");
            validateSafeText(step.getInstruction(), "instruction");
            validateSafeText(step.getExpectedEvidence(), "expectedEvidence");
        }
        return new ValidationResult(true, "处置剧本安全预检通过");
    }

    @Transactional
    public PlaybookDetail publish(Long id) {
        validateExisting(id);
        SocResponsePlaybook playbook = existingPlaybook(id);
        playbook.setStatus("active");
        playbook.setEnabled(1);
        playbook.setApprovedBy(securityScope.currentUserId());
        playbook.setApprovedAt(LocalDateTime.now());
        playbookMapper.updateById(playbook);
        return detail(id);
    }

    @Transactional
    public PlaybookDetail disable(Long id) {
        SocResponsePlaybook playbook = existingPlaybook(id);
        playbook.setStatus("disabled");
        playbook.setEnabled(0);
        playbook.setUpdatedBy(securityScope.currentUserId());
        playbookMapper.updateById(playbook);
        return detail(id);
    }

    public List<PlaybookSuggestion> suggestionsForAlert(Long alertId) {
        SocAlert alert = socOperationService.alertDetail(alertId);
        List<SocResponsePlaybook> candidates = playbookMapper.selectList(new LambdaQueryWrapper<SocResponsePlaybook>()
                .eq(SocResponsePlaybook::getStatus, "active")
                .eq(SocResponsePlaybook::getEnabled, 1)
                .eq(SocResponsePlaybook::getDeleted, 0)
                .orderByAsc(SocResponsePlaybook::getSortOrder));
        return candidates.stream()
                .map(playbook -> suggestion(alert, playbook))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public ApplyPlaybookResult applyToAlert(Long alertId, Long playbookId, String remark) {
        SocAlert alert = socOperationService.alertDetail(alertId);
        SocResponsePlaybook playbook = existingPlaybook(playbookId);
        if (!isActive(playbook)) {
            throw new BusinessException("处置剧本未发布或已停用");
        }
        PlaybookSuggestion suggestion = suggestion(alert, playbook);
        if (suggestion == null) {
            throw new BusinessException("该处置剧本不匹配当前告警");
        }
        SocTicket ticket = socOperationService.createTicket(alertId, new AlertActionRequest(firstNotBlank(remark, "应用处置剧本：" + playbook.getPlaybookName()), null));
        List<SocTicketTask> existing = tasksByTicketAndPlaybook(ticket.getId(), playbook.getId());
        if (!existing.isEmpty()) {
            writeMatchLog(alert.getId(), playbook.getId(), ticket.getId(), suggestion.matchReason(), "reused");
            return new ApplyPlaybookResult(playbook, ticket, existing, existing.size(), 0, "该剧本已应用，已复用现有任务清单。");
        }
        List<SocTicketTask> created = new ArrayList<>();
        for (SocResponsePlaybookStep step : suggestion.steps()) {
            SocTicketTask task = new SocTicketTask();
            task.setTicketId(ticket.getId());
            task.setAlertId(alert.getId());
            task.setPlaybookId(playbook.getId());
            task.setPlaybookStepId(step.getId());
            task.setTaskKey(playbook.getPlaybookKey() + ":" + step.getStepKey());
            task.setTaskName(step.getStepName());
            task.setTaskType(step.getStepType());
            boolean employeeTask = Integer.valueOf(1).equals(step.getRequiresEmployee()) || "employee".equalsIgnoreCase(step.getOwnerRole());
            task.setAssigneeType(employeeTask ? "employee" : "analyst");
            task.setAssigneeId(employeeTask ? firstNonNull(alert.getOwnerId(), securityScope.currentUserId()) : firstNonNull(ticket.getAssigneeId(), securityScope.currentUserId()));
            task.setAssigneeName(employeeTask ? null : ticket.getAssigneeName());
            task.setInstruction(step.getInstruction());
            task.setExpectedEvidence(step.getExpectedEvidence());
            task.setStatus("pending");
            task.setSortOrder(step.getSortOrder());
            task.setDeleted(0);
            taskMapper.insert(task);
            created.add(task);
        }
        appendTimeline(ticket.getId(), "应用处置剧本", ticket.getStatus(), ticket.getStatus(),
                playbook.getPlaybookName() + "；生成 " + created.size() + " 个处置任务");
        writeMatchLog(alert.getId(), playbook.getId(), ticket.getId(), suggestion.matchReason(), "applied");
        return new ApplyPlaybookResult(playbook, ticket, created, created.size(), employeeTaskCount(created), "已应用处置剧本并生成任务清单。");
    }

    public List<SocTicketTask> ticketTasks(Long ticketId) {
        SocTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        ensureTicketAccess(ticket);
        return tasksByTicket(ticketId);
    }

    @Transactional
    public SocTicketTask startTask(Long taskId, TaskActionRequest request) {
        SocTicketTask task = accessibleTask(taskId);
        if ("completed".equals(task.getStatus()) || "skipped".equals(task.getStatus())) {
            throw new BusinessException("任务已结束，不能重新开始");
        }
        task.setStatus("in_progress");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        appendTimeline(task.getTicketId(), "处置任务开始", null, null, task.getTaskName() + optionalRemark(request));
        return task;
    }

    @Transactional
    public SocTicketTask completeTask(Long taskId, TaskActionRequest request) {
        SocTicketTask task = accessibleTask(taskId);
        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        task.setEvidenceText(trimToNull(request == null ? null : request.evidenceText()));
        taskMapper.updateById(task);
        appendTimeline(task.getTicketId(), "处置任务完成", null, null, task.getTaskName() + optionalRemark(request));
        return task;
    }

    @Transactional
    public SocTicketTask skipTask(Long taskId, TaskActionRequest request) {
        SocTicketTask task = accessibleTask(taskId);
        task.setStatus("skipped");
        task.setSkippedAt(LocalDateTime.now());
        task.setEvidenceText(trimToNull(request == null ? null : request.evidenceText()));
        taskMapper.updateById(task);
        appendTimeline(task.getTicketId(), "处置任务跳过", null, null, task.getTaskName() + optionalRemark(request));
        return task;
    }

    public List<SocTicketTask> employeeTasks() {
        Long userId = securityScope.currentUserId();
        if (userId == null) {
            return List.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getAssigneeType, "employee")
                .eq(SocTicketTask::getAssigneeId, userId)
                .eq(SocTicketTask::getDeleted, 0)
                .orderByAsc(SocTicketTask::getSortOrder)
                .orderByDesc(SocTicketTask::getCreatedAt)
                .last("LIMIT 100"));
    }

    public SocTicketTask employeeTaskDetail(Long taskId) {
        return employeeTask(taskId);
    }

    @Transactional
    public SocTicketTask submitEmployeeEvidence(Long taskId, TaskActionRequest request) {
        SocTicketTask task = employeeTask(taskId);
        task.setEvidenceText(trimToNull(request == null ? null : request.evidenceText()));
        task.setStatus("submitted");
        taskMapper.updateById(task);
        appendTimeline(task.getTicketId(), "员工提交证据", null, null, task.getTaskName() + optionalRemark(request));
        writeEmployeeTaskLog(task, "submit", firstNotBlank(request == null ? null : request.remark(), request == null ? null : request.evidenceText(), "员工提交待办处理说明"));
        return task;
    }

    @Transactional
    public SocTicketTask confirmEmployeeTask(Long taskId, TaskActionRequest request) {
        SocTicketTask task = employeeTask(taskId);
        task.setStatus("confirmed");
        task.setCompletedAt(LocalDateTime.now());
        if (notBlank(request == null ? null : request.evidenceText())) {
            task.setEvidenceText(request.evidenceText().trim());
        }
        taskMapper.updateById(task);
        appendTimeline(task.getTicketId(), "员工确认任务", null, null, task.getTaskName() + optionalRemark(request));
        writeEmployeeTaskLog(task, "confirm", firstNotBlank(request == null ? null : request.remark(), request == null ? null : request.evidenceText(), "员工确认待办已处理"));
        return task;
    }

    public PlaybookStats statsForTickets(List<Long> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return new PlaybookStats(0, 0, 0, 0);
        }
        List<SocTicketTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .in(SocTicketTask::getTicketId, ticketIds)
                .eq(SocTicketTask::getDeleted, 0));
        long completed = tasks.stream().filter(task -> List.of("completed", "confirmed").contains(task.getStatus())).count();
        long employee = tasks.stream().filter(task -> "employee".equals(task.getAssigneeType())).count();
        long pending = tasks.stream().filter(task -> !List.of("completed", "confirmed", "skipped").contains(task.getStatus())).count();
        return new PlaybookStats(tasks.size(), completed, employee, pending);
    }

    private void applyRequest(SocResponsePlaybook playbook, PlaybookRequest request) {
        playbook.setPlaybookKey(request.playbookKey().trim());
        playbook.setPlaybookName(request.playbookName().trim());
        playbook.setSourceType(request.sourceType().trim().toLowerCase(Locale.ROOT));
        playbook.setEventType(trimToNull(request.eventType()));
        playbook.setRuleIdPattern(trimToNull(request.ruleIdPattern()));
        playbook.setMinSeverity(firstNotBlank(request.minSeverity(), "low").toLowerCase(Locale.ROOT));
        playbook.setMatchExpression(trimToNull(request.matchExpression()));
        playbook.setDescription(trimToNull(request.description()));
        playbook.setStatus(firstNotBlank(request.status(), "draft"));
        playbook.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        playbook.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        playbook.setSafetyNote(firstNotBlank(request.safetyNote(), "只生成处置建议和任务清单，不执行命令、不自动修复、不调用外部系统。"));
    }

    private void replaceSteps(Long playbookId, List<PlaybookStepRequest> steps) {
        stepMapper.delete(new LambdaUpdateWrapper<SocResponsePlaybookStep>().eq(SocResponsePlaybookStep::getPlaybookId, playbookId));
        int index = 0;
        for (PlaybookStepRequest request : safeSteps(steps)) {
            SocResponsePlaybookStep step = new SocResponsePlaybookStep();
            step.setPlaybookId(playbookId);
            step.setStepKey(request.stepKey().trim());
            step.setStepName(request.stepName().trim());
            step.setStepType(request.stepType().trim());
            step.setOwnerRole(firstNotBlank(request.ownerRole(), "analyst"));
            step.setInstruction(request.instruction().trim());
            step.setExpectedEvidence(trimToNull(request.expectedEvidence()));
            step.setRequiresEmployee(Boolean.TRUE.equals(request.requiresEmployee()) ? 1 : 0);
            step.setSortOrder(request.sortOrder() == null ? (index + 1) * 10 : request.sortOrder());
            step.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
            step.setDeleted(0);
            stepMapper.insert(step);
            index += 1;
        }
    }

    private List<PlaybookStepRequest> safeSteps(List<PlaybookStepRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new BusinessException("处置剧本至少需要一个人工步骤");
        }
        return steps;
    }

    private void validateRequest(PlaybookRequest request) {
        validateSafeText(request.playbookKey(), "playbookKey");
        validateSafeText(request.playbookName(), "playbookName");
        validateSafeText(request.description(), "description");
        validateSafeText(request.matchExpression(), "matchExpression");
        for (PlaybookStepRequest step : safeSteps(request.steps())) {
            validateSafeText(step.stepName(), "stepName");
            validateSafeText(step.instruction(), "instruction");
            validateSafeText(step.expectedEvidence(), "expectedEvidence");
        }
    }

    private void validateSafeText(String text, String field) {
        if (!notBlank(text)) {
            return;
        }
        for (String marker : FORBIDDEN_METACHARS) {
            if (text.contains(marker)) {
                throw new BusinessException(field + " 包含不允许的 shell 元字符");
            }
        }
        if (FORBIDDEN_TEXT.matcher(text).find()) {
            throw new BusinessException(field + " 包含攻击、脚本、扫描、下载或自动修复语义");
        }
    }

    private PlaybookSuggestion suggestion(SocAlert alert, SocResponsePlaybook playbook) {
        if (!matchesSource(alert.getSourceType(), playbook.getSourceType())) {
            return null;
        }
        if (notBlank(playbook.getEventType()) && !matchesToken(alert.getEventType(), playbook.getEventType())) {
            return null;
        }
        if (notBlank(playbook.getRuleIdPattern()) && !matchesToken(alert.getRuleId(), playbook.getRuleIdPattern())) {
            return null;
        }
        if (severityRank(alert.getSeverity()) < severityRank(playbook.getMinSeverity())) {
            return null;
        }
        List<SocResponsePlaybookStep> steps = stepsOf(playbook.getId()).stream()
                .filter(step -> step.getEnabled() == null || step.getEnabled() == 1)
                .toList();
        if (steps.isEmpty()) {
            return null;
        }
        String reason = "sourceType=" + alert.getSourceType()
                + "，eventType=" + firstNotBlank(alert.getEventType(), "-")
                + "，severity=" + alert.getSeverity()
                + "，ruleId=" + firstNotBlank(alert.getRuleId(), "-");
        return new PlaybookSuggestion(playbook, steps, reason);
    }

    private boolean matchesSource(String alertSource, String playbookSource) {
        if (!notBlank(playbookSource) || "*".equals(playbookSource)) {
            return true;
        }
        return Arrays.stream(playbookSource.split(","))
                .map(String::trim)
                .anyMatch(source -> source.equalsIgnoreCase(alertSource));
    }

    private boolean matchesToken(String value, String expected) {
        if (!notBlank(expected) || "*".equals(expected)) {
            return true;
        }
        if (!notBlank(value)) {
            return false;
        }
        return Arrays.stream(expected.split(","))
                .map(String::trim)
                .anyMatch(token -> "*".equals(token) || token.equalsIgnoreCase(value) || value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT)));
    }

    private int severityRank(String severity) {
        return switch (String.valueOf(severity).toLowerCase(Locale.ROOT)) {
            case "critical", "严重" -> 4;
            case "high", "高危" -> 3;
            case "medium", "中危" -> 2;
            case "low", "低危" -> 1;
            default -> 0;
        };
    }

    private boolean isActive(SocResponsePlaybook playbook) {
        return "active".equals(playbook.getStatus()) && Integer.valueOf(1).equals(playbook.getEnabled());
    }

    private SocResponsePlaybook existingPlaybook(Long id) {
        SocResponsePlaybook playbook = playbookMapper.selectById(id);
        if (playbook == null || Integer.valueOf(1).equals(playbook.getDeleted())) {
            throw new BusinessException("处置剧本不存在");
        }
        return playbook;
    }

    private List<SocResponsePlaybookStep> stepsOf(Long playbookId) {
        return stepMapper.selectList(new LambdaQueryWrapper<SocResponsePlaybookStep>()
                .eq(SocResponsePlaybookStep::getPlaybookId, playbookId)
                .eq(SocResponsePlaybookStep::getDeleted, 0)
                .orderByAsc(SocResponsePlaybookStep::getSortOrder));
    }

    private List<SocTicketTask> tasksByTicket(Long ticketId) {
        return taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getTicketId, ticketId)
                .eq(SocTicketTask::getDeleted, 0)
                .orderByAsc(SocTicketTask::getSortOrder)
                .orderByAsc(SocTicketTask::getId));
    }

    private List<SocTicketTask> tasksByTicketAndPlaybook(Long ticketId, Long playbookId) {
        return taskMapper.selectList(new LambdaQueryWrapper<SocTicketTask>()
                .eq(SocTicketTask::getTicketId, ticketId)
                .eq(SocTicketTask::getPlaybookId, playbookId)
                .eq(SocTicketTask::getDeleted, 0)
                .orderByAsc(SocTicketTask::getSortOrder));
    }

    private SocTicketTask accessibleTask(Long taskId) {
        SocTicketTask task = taskMapper.selectById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BusinessException("处置任务不存在");
        }
        SocTicket ticket = ticketMapper.selectById(task.getTicketId());
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        if (!securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId())) {
            throw new BusinessException("无权访问该处置任务");
        }
        return task;
    }

    private SocTicketTask employeeTask(Long taskId) {
        SocTicketTask task = taskMapper.selectById(taskId);
        Long userId = securityScope.currentUserId();
        if (task == null || !Objects.equals(task.getAssigneeId(), userId) || !"employee".equals(task.getAssigneeType())) {
            throw new BusinessException("无权访问该员工待办");
        }
        return task;
    }

    private void ensureTicketAccess(SocTicket ticket) {
        if (!securityScope.canAccess(ticket.getAssigneeId(), ticket.getDeptId())) {
            throw new BusinessException("无权访问该工单");
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
        timeline.setDeleted(0);
        timelineMapper.insert(timeline);
    }

    private void writeEmployeeTaskLog(SocTicketTask task, String actionType, String note) {
        SocAlert alert = task.getAlertId() == null ? null : alertMapper.selectById(task.getAlertId());
        if (alert == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SocExternalEvent event = new SocExternalEvent();
        event.setEventUid("CLIENT-TASK-" + actionType.toUpperCase(Locale.ROOT) + "-" + task.getId() + "-" + now.toString().replaceAll("[^0-9]", ""));
        event.setSourceType("client-task");
        event.setEventType("confirm".equals(actionType) ? "employee_task_confirm" : "employee_task_submit");
        event.setSeverity("low");
        event.setRuleId("CLIENT-TASK-ACTION");
        event.setRuleName("员工待办处理记录");
        event.setAssetName(alert.getAssetName());
        event.setAssetIp(alert.getAssetIp());
        event.setAlertId(alert.getId());
        event.setStatus("closed");
        event.setOwnerId(task.getAssigneeId());
        event.setDeptId(alert.getDeptId());
        event.setEventTime(now);
        event.setNormalizedEvent("{\"taskId\":" + task.getId()
                + ",\"ticketId\":" + task.getTicketId()
                + ",\"actionType\":\"" + actionType + "\""
                + ",\"note\":\"" + escapeJson(firstNotBlank(note, "")) + "\"}");
        externalEventMapper.insert(event);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private void writeMatchLog(Long alertId, Long playbookId, Long ticketId, String reason, String status) {
        SocPlaybookMatchLog log = new SocPlaybookMatchLog();
        log.setAlertId(alertId);
        log.setPlaybookId(playbookId);
        log.setTicketId(ticketId);
        log.setMatchReason(reason);
        log.setApplyStatus(status);
        log.setOperatorId(securityScope.currentUserId());
        log.setOperatorName(securityScope.currentUsername());
        log.setDeleted(0);
        matchLogMapper.insert(log);
    }

    private int employeeTaskCount(List<SocTicketTask> tasks) {
        return (int) tasks.stream().filter(task -> "employee".equals(task.getAssigneeType())).count();
    }

    private String optionalRemark(TaskActionRequest request) {
        String remark = request == null ? null : request.remark();
        return notBlank(remark) ? "；" + remark.trim() : "";
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    public record PlaybookDetail(SocResponsePlaybook playbook, List<SocResponsePlaybookStep> steps) {
    }

    public record PlaybookSuggestion(SocResponsePlaybook playbook, List<SocResponsePlaybookStep> steps, String matchReason) {
    }

    public record ApplyPlaybookResult(SocResponsePlaybook playbook, SocTicket ticket, List<SocTicketTask> tasks,
                                      int createdTasks, int employeeTasks, String message) {
    }

    public record ValidationResult(boolean passed, String message) {
    }

    public record PlaybookStats(long totalTasks, long completedTasks, long employeeTasks, long pendingTasks) {
    }
}
