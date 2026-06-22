package com.zhangjiyan.template.soc.asset;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.correlation.CorrelationService;
import com.zhangjiyan.template.soc.correlation.SocIncidentCluster;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import com.zhangjiyan.template.soc.recommendation.RecommendationService;
import com.zhangjiyan.template.soc.risk.SocAssetRiskSnapshot;
import com.zhangjiyan.template.soc.risk.RiskScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SOC 资产视图", description = "资产风险与数据范围查询")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/assets")
public class SocAssetController {

    private final SocOperationService service;
    private final RiskScoringService riskScoringService;
    private final CorrelationService correlationService;
    private final RecommendationService recommendationService;

    @Operation(summary = "分页查询资产")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:asset:view')")
    public ApiResult<PageResult<SocAsset>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.assets(request));
    }

    @Operation(summary = "查询资产风险画像")
    @GetMapping("/{id}/risk-profile")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-score:view') or hasAuthority('soc:asset:view')")
    public ApiResult<RiskScoringService.AssetRiskProfile> riskProfile(@PathVariable Long id) {
        return ApiResult.ok(riskScoringService.profile(id));
    }

    @Operation(summary = "查询资产风险趋势")
    @GetMapping("/{id}/risk-history")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:risk-score:view') or hasAuthority('soc:asset:view')")
    public ApiResult<List<SocAssetRiskSnapshot>> riskHistory(@PathVariable Long id) {
        return ApiResult.ok(riskScoringService.history(id));
    }

    @Operation(summary = "查询资产关联事件簇")
    @GetMapping("/{id}/incidents")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:incident:list') or hasAuthority('soc:asset:view')")
    public ApiResult<List<SocIncidentCluster>> incidents(@PathVariable Long id) {
        return ApiResult.ok(correlationService.incidentsForAsset(id));
    }

    @Operation(summary = "查询资产推荐处理顺序")
    @GetMapping("/{id}/recommendations")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:asset:view') or hasAuthority('soc:risk-score:view')")
    public ApiResult<List<RecommendationService.RecommendationItem>> recommendations(@PathVariable Long id,
                                                                                     @org.springframework.web.bind.annotation.RequestParam(defaultValue = "8") int limit) {
        return ApiResult.ok(recommendationService.assetRecommendations(id, limit));
    }
}
