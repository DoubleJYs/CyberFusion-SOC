package com.zhangjiyan.template.soc.keeper;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工端安全管家", description = "Web 版只读体检聚合，不执行修复、命令或扫描")
@RestController
@RequiredArgsConstructor
@RequestMapping("/client/security-keeper")
public class SecurityKeeperController {

    private final SecurityKeeperService service;

    @Operation(summary = "生成当前电脑一键体检结果")
    @PostMapping("/checkup")
    @OperationAudit("CLIENT_SECURITY_KEEPER.CHECKUP")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SecurityKeeperService.CheckupDetail> checkup(@Valid @RequestBody SecurityKeeperCheckupRequest request) {
        return ApiResult.ok(service.run(request.assetIp()));
    }

    @Operation(summary = "查询当前账号可见体检历史")
    @GetMapping("/checkups")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<SecurityKeeperService.CheckupSummary>> checkups(@RequestParam(required = false) String assetIp) {
        return ApiResult.ok(service.history(assetIp));
    }

    @Operation(summary = "查询体检详情")
    @GetMapping("/checkups/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SecurityKeeperService.CheckupDetail> detail(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id));
    }

    @Operation(summary = "查询当前电脑安全日志")
    @GetMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<SecurityKeeperService.SecurityLogItem>> logs(@RequestParam String assetIp) {
        return ApiResult.ok(service.logs(assetIp));
    }

    @Operation(summary = "查询当前电脑风险修复建议")
    @GetMapping("/recommendations")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<SecurityKeeperService.RepairRecommendation>> recommendations(@RequestParam String assetIp) {
        return ApiResult.ok(service.recommendations(assetIp));
    }

    @Operation(summary = "员工确认修复建议已处理")
    @PostMapping("/recommendations/{id}/confirm")
    @OperationAudit("CLIENT_SECURITY_KEEPER.RECOMMENDATION_CONFIRM")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SecurityKeeperService.RepairRecommendation> confirmRecommendation(@PathVariable String id,
                                                                                       @Valid @RequestBody(required = false) SecurityKeeperRecommendationRequest request) {
        return ApiResult.ok(service.confirmRecommendation(id, request == null ? null : request.note()));
    }

    @Operation(summary = "员工提交修复建议说明")
    @PostMapping("/recommendations/{id}/submit-note")
    @OperationAudit("CLIENT_SECURITY_KEEPER.RECOMMENDATION_NOTE")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SecurityKeeperService.RepairRecommendation> submitRecommendationNote(@PathVariable String id,
                                                                                         @Valid @RequestBody(required = false) SecurityKeeperRecommendationRequest request) {
        return ApiResult.ok(service.submitRecommendationNote(id, request == null ? null : request.note()));
    }
}
