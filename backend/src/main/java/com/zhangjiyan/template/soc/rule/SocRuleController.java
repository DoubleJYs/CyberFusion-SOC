package com.zhangjiyan.template.soc.rule;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "SOC 检测规则中心", description = "检测规则列表、命中预览和 adapter 字段映射")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/rules")
public class SocRuleController {

    private final SocOperationService service;

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
}
