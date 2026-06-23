package com.zhangjiyan.template.soc.algorithm;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/algorithm-center")
public class AlgorithmCenterController {

    private final AlgorithmGovernanceService service;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:algorithm:view')")
    public ApiResult<AlgorithmGovernanceService.AlgorithmOverview> overview() {
        return ApiResult.ok(service.overview());
    }

    @PostMapping("/replay")
    @OperationAudit("SOC_ALGORITHM.REPLAY_DRY_RUN")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:algorithm:replay')")
    public ApiResult<AlgorithmGovernanceService.AlgorithmReplayResult> replay(
            @RequestBody(required = false) AlgorithmGovernanceService.AlgorithmReplayRequest request) {
        return ApiResult.ok(service.replay(request));
    }

    @GetMapping("/evaluations")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:algorithm:view') or hasAuthority('soc:algorithm:evaluation')")
    public ApiResult<PageResult<SocAlgorithmEvaluation>> evaluations(@RequestParam(defaultValue = "1") long pageNum,
                                                                     @RequestParam(defaultValue = "10") long pageSize,
                                                                     @RequestParam(required = false) String algorithmType,
                                                                     @RequestParam(required = false) String batchId) {
        return ApiResult.ok(service.evaluations(pageNum, pageSize, algorithmType, batchId));
    }

    @GetMapping("/evaluations/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:algorithm:view') or hasAuthority('soc:algorithm:evaluation')")
    public ApiResult<AlgorithmGovernanceService.AlgorithmEvaluationDetail> evaluationDetail(@PathVariable Long id) {
        return ApiResult.ok(service.evaluationDetail(id));
    }
}
