package com.zhangjiyan.template.soc.asset;

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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SOC 资产视图", description = "资产风险与数据范围查询")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/assets")
public class SocAssetController {

    private final SocOperationService service;

    @Operation(summary = "分页查询资产")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:asset:view')")
    public ApiResult<PageResult<SocAsset>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.assets(request));
    }
}
