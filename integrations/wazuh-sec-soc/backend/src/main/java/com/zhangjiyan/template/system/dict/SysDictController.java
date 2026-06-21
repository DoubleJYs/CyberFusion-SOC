package com.zhangjiyan.template.system.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.dict.dto.DictDataRequest;
import com.zhangjiyan.template.system.dict.dto.DictTypeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Dict", description = "字典类型与字典数据")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system")
public class SysDictController {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @Operation(summary = "字典类型分页")
    @GetMapping("/dict-types")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:view')")
    public ApiResult<PageResult<SysDictType>> pageTypes(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status) {
        Page<SysDictType> page = dictTypeMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysDictType>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysDictType::getDictName, keyword).or().like(SysDictType::getDictCode, keyword))
                .eq(status != null, SysDictType::getStatus, status)
                .orderByDesc(SysDictType::getCreatedAt));
        return ApiResult.ok(PageResult.from(page));
    }

    @Operation(summary = "新增字典类型")
    @PostMapping("/dict-types")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:create')")
    public ApiResult<Void> createType(@Valid @RequestBody DictTypeRequest request) {
        validateStatus(request.status());
        ensureDictCodeUnique(request.dictCode(), null);
        SysDictType type = new SysDictType();
        type.setDictName(request.dictName());
        type.setDictCode(request.dictCode());
        type.setStatus(request.status() == null ? 1 : request.status());
        dictTypeMapper.insert(type);
        return ApiResult.ok();
    }

    @Operation(summary = "编辑字典类型")
    @PutMapping("/dict-types/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:update')")
    public ApiResult<Void> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeRequest request) {
        SysDictType type = requireType(id);
        validateStatus(request.status());
        ensureDictCodeUnique(request.dictCode(), id);
        type.setDictName(request.dictName());
        type.setDictCode(request.dictCode());
        type.setStatus(request.status() == null ? type.getStatus() : request.status());
        dictTypeMapper.updateById(type);
        return ApiResult.ok();
    }

    @Operation(summary = "删除字典类型")
    @DeleteMapping("/dict-types/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:delete')")
    public ApiResult<Void> deleteType(@PathVariable Long id) {
        requireType(id);
        Long dataCount = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictTypeId, id));
        if (dataCount > 0) {
            throw new BusinessException("请先删除字典数据");
        }
        dictTypeMapper.deleteById(id);
        return ApiResult.ok();
    }

    @Operation(summary = "字典数据分页")
    @GetMapping("/dict-data")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:view')")
    public ApiResult<PageResult<SysDictData>> pageData(@RequestParam(defaultValue = "1") long pageNum,
                                                       @RequestParam(defaultValue = "10") long pageSize,
                                                       @RequestParam(required = false) Long dictTypeId,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer status) {
        Page<SysDictData> page = dictDataMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysDictData>()
                .eq(dictTypeId != null, SysDictData::getDictTypeId, dictTypeId)
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysDictData::getDictLabel, keyword).or().like(SysDictData::getDictValue, keyword))
                .eq(status != null, SysDictData::getStatus, status)
                .orderByAsc(SysDictData::getSortOrder));
        return ApiResult.ok(PageResult.from(page));
    }

    @Operation(summary = "新增字典数据")
    @PostMapping("/dict-data")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:create')")
    public ApiResult<Void> createData(@Valid @RequestBody DictDataRequest request) {
        requireType(request.dictTypeId());
        validateStatus(request.status());
        dictDataMapper.insert(toData(new SysDictData(), request));
        return ApiResult.ok();
    }

    @Operation(summary = "编辑字典数据")
    @PutMapping("/dict-data/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:update')")
    public ApiResult<Void> updateData(@PathVariable Long id, @Valid @RequestBody DictDataRequest request) {
        requireType(request.dictTypeId());
        validateStatus(request.status());
        dictDataMapper.updateById(toData(requireData(id), request));
        return ApiResult.ok();
    }

    @Operation(summary = "删除字典数据")
    @DeleteMapping("/dict-data/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dict:delete')")
    public ApiResult<Void> deleteData(@PathVariable Long id) {
        requireData(id);
        dictDataMapper.deleteById(id);
        return ApiResult.ok();
    }

    private SysDictType requireType(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    private SysDictData requireData(Long id) {
        SysDictData data = dictDataMapper.selectById(id);
        if (data == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典数据不存在");
        }
        return data;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "状态只能是 0 或 1");
        }
    }

    private void ensureDictCodeUnique(String dictCode, Long excludeId) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictCode, dictCode)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("字典编码已存在");
        }
    }

    private SysDictData toData(SysDictData data, DictDataRequest request) {
        data.setDictTypeId(request.dictTypeId());
        data.setDictLabel(request.dictLabel());
        data.setDictValue(request.dictValue());
        data.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        data.setStatus(request.status() == null ? 1 : request.status());
        return data;
    }
}
