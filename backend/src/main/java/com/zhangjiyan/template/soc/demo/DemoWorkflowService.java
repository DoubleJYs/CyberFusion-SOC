package com.zhangjiyan.template.soc.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.workflow.SysBizFlowLog;
import com.zhangjiyan.template.system.workflow.SysBizFlowLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemoWorkflowService {

    private static final String BIZ_TYPE = "SOC_DEMO_WORKFLOW";

    private final SocDemoWorkflowRunMapper runMapper;
    private final SocDemoWorkflowArchiveMapper archiveMapper;
    private final SysBizFlowLogMapper flowLogMapper;
    private final ObjectMapper objectMapper;

    public List<DemoWorkflowResponse> activeRuns() {
        return runMapper.selectList(new LambdaQueryWrapper<SocDemoWorkflowRun>()
                        .orderByDesc(SocDemoWorkflowRun::getUpdatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DemoWorkflowResponse save(DemoWorkflowRequest request) {
        SocDemoWorkflowRun run = findActive(request.runId());
        LocalDateTime now = LocalDateTime.now();
        boolean created = run == null;
        String previousStatus = created ? "" : run.getStatus();
        if (run == null) {
            run = new SocDemoWorkflowRun();
            run.setRunId(request.runId());
            run.setCreatedAt(now);
            var currentUser = SecurityUtils.currentUser().orElse(null);
            if (currentUser != null) {
                run.setCreatedBy(currentUser.userId());
                run.setCreatedByName(currentUser.nickname());
            }
        }
        run.setBatchId(request.batchId());
        run.setSelectedCaseId(request.selectedCaseId());
        run.setStepKey(request.stepKey());
        run.setStatus(request.status());
        run.setCountsJson(writeJson(request.counts()));
        run.setLogsJson(writeJson(request.logs()));
        run.setUpdatedAt(now);
        run.setLastVisitedAt(now);
        if (created) {
            runMapper.insert(run);
        } else {
            runMapper.updateById(run);
        }
        writeFlowLog(run, created ? "create" : "update", previousStatus, run.getStatus(),
                created ? "创建安全验证工作流" : "更新安全验证工作流");
        return toResponse(run);
    }

    @Transactional
    public DemoWorkflowResponse archive(String runId, DemoWorkflowArchiveRequest request) {
        SocDemoWorkflowRun run = requireActive(runId);
        if (!"completed".equals(run.getStatus())) {
            throw new BusinessException("仅已完成的安全验证工作流可以归档");
        }
        LocalDateTime now = LocalDateTime.now();
        SocDemoWorkflowArchive archive = new SocDemoWorkflowArchive();
        archive.setRunId(run.getRunId());
        archive.setBatchId(run.getBatchId());
        archive.setSelectedCaseId(run.getSelectedCaseId());
        archive.setFinalStepKey(run.getStepKey());
        archive.setFinalStatus(run.getStatus());
        archive.setCountsJson(run.getCountsJson());
        archive.setLogsJson(run.getLogsJson());
        archive.setCreatedBy(run.getCreatedBy());
        archive.setCreatedByName(run.getCreatedByName());
        archive.setCreatedAt(run.getCreatedAt());
        archive.setUpdatedAt(run.getUpdatedAt());
        archive.setArchivedAt(now);
        archive.setArchiveReason(request.reason());
        SecurityUtils.currentUser().ifPresent(user -> {
            archive.setArchivedBy(user.userId());
            archive.setArchivedByName(user.nickname());
        });
        archiveMapper.insert(archive);
        runMapper.deleteById(run.getId());
        writeFlowLog(run, "archive", "completed", "archived", request.reason() == null || request.reason().isBlank()
                ? "完成安全验证后自动归档" : request.reason());
        return toResponse(run);
    }

    @Transactional
    public void delete(String runId) {
        SocDemoWorkflowRun run = requireActive(runId);
        runMapper.deleteById(run.getId());
        writeFlowLog(run, "delete", run.getStatus(), "deleted", "删除未归档安全验证工作流");
    }

    private SocDemoWorkflowRun findActive(String runId) {
        return runMapper.selectOne(new LambdaQueryWrapper<SocDemoWorkflowRun>()
                .eq(SocDemoWorkflowRun::getRunId, runId)
                .last("LIMIT 1"));
    }

    private SocDemoWorkflowRun requireActive(String runId) {
        SocDemoWorkflowRun run = findActive(runId);
        if (run == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "安全验证工作流不存在或已归档");
        }
        return run;
    }

    private void writeFlowLog(SocDemoWorkflowRun run, String action, String fromStatus, String toStatus, String remark) {
        SysBizFlowLog log = new SysBizFlowLog();
        log.setBizType(BIZ_TYPE);
        log.setBizId(run.getRunId());
        log.setBizNo(run.getBatchId());
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setRemark(remark);
        SecurityUtils.currentUser().ifPresent(user -> {
            log.setOperatorId(user.userId());
            log.setOperatorName(user.nickname());
        });
        flowLogMapper.insert(log);
    }

    private DemoWorkflowResponse toResponse(SocDemoWorkflowRun run) {
        return new DemoWorkflowResponse(
                run.getRunId(), run.getBatchId(), run.getSelectedCaseId(), run.getStepKey(), run.getStatus(),
                run.getCreatedAt(), run.getUpdatedAt(), run.getLastVisitedAt(), run.getCreatedBy(), run.getCreatedByName(),
                readCounts(run.getCountsJson()), readLogs(run.getLogsJson())
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("工作流记录序列化失败");
        }
    }

    private Map<String, Integer> readCounts(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new BusinessException("工作流统计记录损坏");
        }
    }

    private List<DemoWorkflowLogRequest> readLogs(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new BusinessException("工作流操作记录损坏");
        }
    }
}
