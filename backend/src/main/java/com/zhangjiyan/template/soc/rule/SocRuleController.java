package com.zhangjiyan.template.soc.rule;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SOC 检测规则中心", description = "检测规则列表、命中预览和 adapter 字段映射")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/rules")
public class SocRuleController {

    private final SocOperationService service;
    private final DetectionRulePolicyService policyService;

    @Operation(summary = "分页查询检测规则")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:rules:view') or hasAuthority('soc:external-event:view')")
    public ApiResult<PageResult<SocOperationService.DetectionRuleSummary>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.detectionRules(request));
    }

    @Operation(summary = "查询规则最近命中")
    @GetMapping("/hits")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:rules:view') or hasAuthority('soc:external-event:view')")
    public ApiResult<SocOperationService.DetectionRuleHits> hits(@RequestParam String sourceType,
                                                                  @RequestParam String ruleId) {
        return ApiResult.ok(service.detectionRuleHits(sourceType, ruleId));
    }

    @Operation(summary = "查询 adapter 字段映射说明")
    @GetMapping("/adapter-mappings")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:rules:view') or hasAuthority('soc:external-event:view')")
    public ApiResult<List<SocOperationService.AdapterFieldMapping>> adapterMappings() {
        return ApiResult.ok(service.adapterFieldMappings());
    }

    @Operation(summary = "分页查询统一检测规则配置")
    @GetMapping("/configs")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:rules:view')")
    public ApiResult<PageResult<SocDetectionRulePolicy>> configs(@RequestParam(defaultValue = "1") long pageNum,
                                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                                  @RequestParam(required = false) String sourceType,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String keyword) {
        return ApiResult.ok(policyService.page(pageNum, pageSize, sourceType, status, keyword));
    }

    @Operation(summary = "新建统一检测规则配置")
    @PostMapping("/configs")
    @OperationAudit("SOC_RULE.CREATE_CONFIG")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:create')")
    public ApiResult<SocDetectionRulePolicy> createConfig(@Valid @RequestBody DetectionRulePolicyRequest request) {
        return ApiResult.ok(policyService.create(request));
    }

    @Operation(summary = "编辑统一检测规则配置")
    @PutMapping("/configs/{id}")
    @OperationAudit("SOC_RULE.UPDATE_CONFIG")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update')")
    public ApiResult<SocDetectionRulePolicy> updateConfig(@PathVariable Long id,
                                                           @Valid @RequestBody DetectionRulePolicyRequest request) {
        return ApiResult.ok(policyService.update(id, request));
    }

    @Operation(summary = "发布统一检测规则配置")
    @PostMapping("/configs/{id}/publish")
    @OperationAudit("SOC_RULE.PUBLISH_CONFIG")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:publish')")
    public ApiResult<SocDetectionRulePolicy> publishConfig(@PathVariable Long id) {
        return ApiResult.ok(policyService.publish(id));
    }

    @Operation(summary = "停用统一检测规则配置")
    @PostMapping("/configs/{id}/disable")
    @OperationAudit("SOC_RULE.DISABLE_CONFIG")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:disable')")
    public ApiResult<SocDetectionRulePolicy> disableConfig(@PathVariable Long id) {
        return ApiResult.ok(policyService.disable(id));
    }
}
