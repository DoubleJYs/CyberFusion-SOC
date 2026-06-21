package com.zhangjiyan.template.system.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Log", description = "登录日志与操作日志")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system")
public class SysOperationLogController {

    private final SysOperationLogMapper operationLogMapper;
    private final SysLoginLogMapper loginLogMapper;

    @Operation(summary = "操作日志分页")
    @GetMapping("/operation-logs")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:log:view')")
    public ApiResult<PageResult<SysOperationLog>> operationLogs(@RequestParam(defaultValue = "1") long pageNum,
                                                                @RequestParam(defaultValue = "10") long pageSize,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String status) {
        Page<SysOperationLog> page = operationLogMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysOperationLog>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysOperationLog::getUsername, keyword).or().like(SysOperationLog::getAction, keyword))
                .eq(status != null && !status.isBlank(), SysOperationLog::getStatus, status)
                .orderByDesc(SysOperationLog::getCreatedAt));
        return ApiResult.ok(PageResult.from(page));
    }

    @Operation(summary = "登录日志分页")
    @GetMapping("/login-logs")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:log:view')")
    public ApiResult<PageResult<SysLoginLog>> loginLogs(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status) {
        Page<SysLoginLog> page = loginLogMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysLoginLog>()
                .like(keyword != null && !keyword.isBlank(), SysLoginLog::getUsername, keyword)
                .eq(status != null && !status.isBlank(), SysLoginLog::getStatus, status)
                .orderByDesc(SysLoginLog::getCreatedAt));
        return ApiResult.ok(PageResult.from(page));
    }
}
