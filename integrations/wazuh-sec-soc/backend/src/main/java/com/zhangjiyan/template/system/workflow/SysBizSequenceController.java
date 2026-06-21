package com.zhangjiyan.template.system.workflow;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.system.workflow.dto.BizNoGenerateRequest;
import com.zhangjiyan.template.system.workflow.dto.BizSequenceCreateRequest;
import com.zhangjiyan.template.system.workflow.dto.BizSequenceUpdateRequest;
import com.zhangjiyan.template.system.workflow.vo.BizNoGenerateVO;
import com.zhangjiyan.template.system.workflow.vo.SysBizSequenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BizSequence", description = "业务编号规则")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/biz-sequences")
public class SysBizSequenceController {

    private final SysBizSequenceService sequenceService;

    @Operation(summary = "编号规则分页")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:sequence:list')")
    public ApiResult<PageResult<SysBizSequenceVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer enabled) {
        return ApiResult.ok(sequenceService.pageSequences(pageNum, pageSize, keyword, enabled));
    }

    @Operation(summary = "新增编号规则")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:sequence:create')")
    public ApiResult<SysBizSequenceVO> create(@Valid @RequestBody BizSequenceCreateRequest request) {
        return ApiResult.ok(sequenceService.create(request));
    }

    @Operation(summary = "编辑编号规则")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:sequence:update')")
    public ApiResult<SysBizSequenceVO> update(@PathVariable Long id, @Valid @RequestBody BizSequenceUpdateRequest request) {
        return ApiResult.ok(sequenceService.update(id, request));
    }

    @Operation(summary = "生成业务编号")
    @PostMapping("/generate")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:sequence:generate')")
    public ApiResult<BizNoGenerateVO> generate(@Valid @RequestBody BizNoGenerateRequest request) {
        return ApiResult.ok(sequenceService.generate(request));
    }
}
