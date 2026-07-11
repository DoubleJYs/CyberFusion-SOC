package com.zhangjiyan.template.soc.demo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DemoWorkflowResponse(
        String id,
        String batchId,
        String selectedCaseId,
        String stepKey,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastVisitedAt,
        Long createdBy,
        String createdByName,
        Map<String, Integer> counts,
        List<DemoWorkflowLogRequest> logs
) {
}
