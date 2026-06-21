package com.zhangjiyan.template.system.user;

import com.zhangjiyan.template.common.dto.PageRequest;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.system.user.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User", description = "用户管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/users")
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "用户分页查询")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:view')")
    public ApiResult<PageResult<UserResponse>> page(@Valid PageRequest request) {
        return ApiResult.ok(userService.pageUsers(request));
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:view')")
    public ApiResult<UserResponse> detail(@PathVariable Long id) {
        return ApiResult.ok(userService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:create')")
    public ApiResult<Void> create(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
        return ApiResult.ok();
    }

    @Operation(summary = "编辑用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.updateUser(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "启用或禁用用户")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:update')")
    public ApiResult<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return ApiResult.ok();
    }

    @Operation(summary = "重置密码")
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:reset-password')")
    public ApiResult<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "分配角色")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:user:assign-role')")
    public ApiResult<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return ApiResult.ok();
    }

    @Operation(summary = "当前用户修改密码")
    @PatchMapping("/me/password")
    public ApiResult<Void> changeCurrentPassword(@Valid @RequestBody PasswordRequest request) {
        userService.changeCurrentPassword(request);
        return ApiResult.ok();
    }
}
