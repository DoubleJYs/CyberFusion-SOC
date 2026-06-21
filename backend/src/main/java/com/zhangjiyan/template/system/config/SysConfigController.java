package com.zhangjiyan.template.system.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.config.dto.ConfigRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Config", description = "系统参数配置")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/configs")
public class SysConfigController {

    private final SysConfigMapper configMapper;

    @Operation(summary = "系统参数分页")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:config:view')")
    public ApiResult<PageResult<SysConfig>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String groupCode,
                                                 @RequestParam(required = false) Integer status) {
        Page<SysConfig> page = configMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysConfig>()
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysConfig::getConfigKey, keyword)
                        .or()
                        .like(SysConfig::getConfigName, keyword))
                .eq(groupCode != null && !groupCode.isBlank(), SysConfig::getGroupCode, groupCode)
                .eq(status != null, SysConfig::getStatus, status)
                .orderByAsc(SysConfig::getGroupCode)
                .orderByAsc(SysConfig::getConfigKey));
        return ApiResult.ok(PageResult.from(page));
    }

    @Operation(summary = "新增系统参数")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:config:create')")
    public ApiResult<Void> create(@Valid @RequestBody ConfigRequest request) {
        Long count = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, request.configKey()));
        if (count > 0) {
            throw new BusinessException("参数键已存在");
        }
        configMapper.insert(toEntity(new SysConfig(), request));
        return ApiResult.ok();
    }

    @Operation(summary = "编辑系统参数")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:config:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody ConfigRequest request) {
        SysConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "参数不存在");
        }
        if (config.getEditable() != null && config.getEditable() == 0) {
            throw new BusinessException("内置参数不允许编辑");
        }
        Long duplicated = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, request.configKey())
                .ne(SysConfig::getId, id));
        if (duplicated > 0) {
            throw new BusinessException("参数键已存在");
        }
        configMapper.updateById(toEntity(config, request));
        return ApiResult.ok();
    }

    @Operation(summary = "删除系统参数")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:config:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        SysConfig config = configMapper.selectById(id);
        if (config == null) {
            return ApiResult.ok();
        }
        if (config.getEditable() != null && config.getEditable() == 0) {
            throw new BusinessException("内置参数不允许删除");
        }
        configMapper.deleteById(id);
        return ApiResult.ok();
    }

    private SysConfig toEntity(SysConfig config, ConfigRequest request) {
        config.setConfigKey(request.configKey());
        config.setConfigName(request.configName());
        config.setConfigValue(request.configValue());
        config.setValueType(request.valueType());
        config.setGroupCode(request.groupCode());
        config.setEditable(request.editable() == null ? 1 : request.editable());
        config.setStatus(request.status() == null ? 1 : request.status());
        config.setRemark(request.remark());
        return config;
    }
}
