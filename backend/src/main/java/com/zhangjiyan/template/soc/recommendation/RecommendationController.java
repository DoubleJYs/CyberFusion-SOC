package com.zhangjiyan.template.soc.recommendation;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SOC 推荐动作", description = "把风险因子、事件簇、漏洞、工单和任务转换为可解释推荐动作")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    @Operation(summary = "今日优先处理建议")
    @GetMapping("/top")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:recommendation:view') or hasAuthority('soc:dashboard:view') or hasAuthority('soc:asset:view') or hasAuthority('soc:ticket:view')")
    public ApiResult<List<RecommendationService.RecommendationItem>> top(@RequestParam(defaultValue = "5") int limit) {
        return ApiResult.ok(service.topRecommendations(limit));
    }

    @Operation(summary = "记录推荐动作采纳")
    @PostMapping("/{key}/record")
    @OperationAudit("SOC_RECOMMENDATION.RECORD")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:recommendation:view') or hasAuthority('soc:dashboard:view') or hasAuthority('soc:ticket:transition') or hasAuthority('soc:incident:ticket') or hasAuthority('soc:alert:view')")
    public ApiResult<RecommendationService.RecommendationActionRecord> record(@PathVariable String key,
                                                                              @Valid @RequestBody(required = false) RecommendationActionRequest request) {
        return ApiResult.ok(service.recordAction(key, request));
    }
}
