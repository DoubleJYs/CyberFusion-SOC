package com.zhangjiyan.template.soc.correlation;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SOC 事件关联规则", description = "结构化事件关联规则生命周期")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/correlation-rules")
public class SocCorrelationRuleController {

    private final CorrelationService service;

    @Operation(summary = "分页查询事件关联规则")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:list')")
    public ApiResult<PageResult<SocCorrelationRule>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.rules(pageNum, pageSize, status, type, keyword));
    }

    @Operation(summary = "事件关联规则详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:list')")
    public ApiResult<SocCorrelationRule> detail(@PathVariable Long id) {
        return ApiResult.ok(service.ruleDetail(id));
    }

    @Operation(summary = "新增事件关联规则")
    @PostMapping
    @OperationAudit("SOC_CORRELATION_RULE.CREATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:create')")
    public ApiResult<SocCorrelationRule> create(@Valid @RequestBody CorrelationRuleRequest request) {
        return ApiResult.ok(service.createRule(request));
    }

    @Operation(summary = "更新事件关联规则")
    @PutMapping("/{id}")
    @OperationAudit("SOC_CORRELATION_RULE.UPDATE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:update')")
    public ApiResult<SocCorrelationRule> update(@PathVariable Long id, @Valid @RequestBody CorrelationRuleRequest request) {
        return ApiResult.ok(service.updateRule(id, request));
    }

    @Operation(summary = "校验事件关联规则")
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:list') or hasAuthority('soc:correlation-rule:update')")
    public ApiResult<CorrelationService.ValidationResult> validate(@PathVariable Long id) {
        return ApiResult.ok(service.validateRule(id));
    }

    @Operation(summary = "发布事件关联规则")
    @PostMapping("/{id}/publish")
    @OperationAudit("SOC_CORRELATION_RULE.PUBLISH")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:publish')")
    public ApiResult<SocCorrelationRule> publish(@PathVariable Long id) {
        return ApiResult.ok(service.publishRule(id));
    }

    @Operation(summary = "停用事件关联规则")
    @PostMapping("/{id}/disable")
    @OperationAudit("SOC_CORRELATION_RULE.DISABLE")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:correlation-rule:disable')")
    public ApiResult<SocCorrelationRule> disable(@PathVariable Long id) {
        return ApiResult.ok(service.disableRule(id));
    }
}
