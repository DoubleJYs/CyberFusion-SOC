package com.zhangjiyan.template.soc.risk;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/risk-scoring")
public class RiskScoringController {

    private final RiskScoringService service;

    @GetMapping("/policies")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:list')")
    public ApiResult<PageResult<SocRiskScoringPolicy>> policies(@RequestParam(defaultValue = "1") long pageNum,
                                                                @RequestParam(defaultValue = "10") long pageSize,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.policies(pageNum, pageSize, status, keyword));
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:list')")
    public ApiResult<SocRiskScoringPolicy> policy(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id));
    }

    @PostMapping("/policies")
    @OperationAudit("SOC_RISK_POLICY.CREATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:create')")
    public ApiResult<SocRiskScoringPolicy> create(@Valid @RequestBody RiskScoringPolicyRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/policies/{id}")
    @OperationAudit("SOC_RISK_POLICY.UPDATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:update')")
    public ApiResult<SocRiskScoringPolicy> update(@PathVariable Long id,
                                                  @Valid @RequestBody RiskScoringPolicyRequest request) {
        return ApiResult.ok(service.update(id, request));
    }

    @PostMapping("/policies/{id}/validate")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:update') or hasAuthority('soc:risk-policy:create')")
    public ApiResult<RiskScoringService.ValidationResult> validate(@PathVariable Long id) {
        return ApiResult.ok(service.validateExisting(id));
    }

    @PostMapping("/policies/{id}/publish")
    @OperationAudit("SOC_RISK_POLICY.PUBLISH")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:publish')")
    public ApiResult<SocRiskScoringPolicy> publish(@PathVariable Long id) {
        return ApiResult.ok(service.publish(id));
    }

    @PostMapping("/policies/{id}/disable")
    @OperationAudit("SOC_RISK_POLICY.DISABLE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-policy:disable')")
    public ApiResult<SocRiskScoringPolicy> disable(@PathVariable Long id) {
        return ApiResult.ok(service.disable(id));
    }

    @PostMapping("/recalculate")
    @OperationAudit("SOC_RISK_SCORE.RECALCULATE_ALL")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-score:recalculate')")
    public ApiResult<RiskScoringService.RecalculateResult> recalculateAll() {
        return ApiResult.ok(service.recalculateAll());
    }

    @PostMapping("/recalculate/{assetId}")
    @OperationAudit("SOC_RISK_SCORE.RECALCULATE_ASSET")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-score:recalculate')")
    public ApiResult<RiskScoringService.AssetRiskProfile> recalculateAsset(@PathVariable Long assetId) {
        return ApiResult.ok(service.recalculate(assetId));
    }

    @GetMapping("/top-assets")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-score:view') or hasAuthority('soc:asset:view')")
    public ApiResult<List<RiskScoringService.AssetRiskProfile>> topAssets(@RequestParam(defaultValue = "5") int limit) {
        return ApiResult.ok(service.topAssets(limit));
    }
}
