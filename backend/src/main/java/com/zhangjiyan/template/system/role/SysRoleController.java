package com.zhangjiyan.template.system.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.role.dto.RoleRequest;
import com.zhangjiyan.template.system.role.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "Role", description = "角色管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/roles")
public class SysRoleController {

    private static final Set<String> DATA_SCOPES = Set.of("self", "dept", "dept_tree", "all", "custom");

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;

    @Operation(summary = "角色分页查询")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:view')")
    public ApiResult<PageResult<RoleResponse>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer status) {
        Page<SysRole> page = roleMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysRole>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysRole::getRoleName, keyword).or().like(SysRole::getRoleCode, keyword))
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getId));
        return ApiResult.ok(new PageResult<>(page.getRecords().stream().map(this::toResponse).toList(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:view')")
    public ApiResult<RoleResponse> detail(@PathVariable Long id) {
        return ApiResult.ok(toResponse(requireRole(id)));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:create')")
    public ApiResult<Void> create(@Valid @RequestBody RoleRequest request) {
        validateStatus(request.status());
        validateDataScope(request.dataScope());
        ensureRoleCodeUnique(request.roleCode(), null);
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDataScope(dataScopeOrDefault(request.dataScope()));
        role.setStatus(request.status() == null ? 1 : request.status());
        roleMapper.insert(role);
        bindMenus(role.getId(), request.menuIds());
        bindDepts(role.getId(), request.deptIds());
        return ApiResult.ok();
    }

    @Operation(summary = "编辑角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        SysRole role = requireRole(id);
        validateStatus(request.status());
        validateDataScope(request.dataScope());
        ensureRoleCodeUnique(request.roleCode(), id);
        role.setRoleName(request.roleName());
        role.setRoleCode(request.roleCode());
        role.setDataScope(dataScopeOrDefault(request.dataScope()));
        role.setStatus(request.status() == null ? role.getStatus() : request.status());
        roleMapper.updateById(role);
        bindMenus(id, request.menuIds());
        bindDepts(id, request.deptIds());
        return ApiResult.ok();
    }

    @Operation(summary = "启用或禁用角色")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:update')")
    public ApiResult<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        SysRole role = requireRole(id);
        validateStatus(status);
        role.setStatus(status);
        roleMapper.updateById(role);
        return ApiResult.ok();
    }

    @Operation(summary = "角色绑定菜单")
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:assign-menu')")
    public ApiResult<Void> menus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        requireRole(id);
        bindMenus(id, menuIds);
        return ApiResult.ok();
    }

    @Operation(summary = "查询角色权限")
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:role:view')")
    public ApiResult<List<Long>> roleMenus(@PathVariable Long id) {
        return ApiResult.ok(menuIds(id));
    }

    private void bindMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deletePhysicalByRoleId(roleId);
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
    }

    private void bindDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.deletePhysicalByRoleId(roleId);
        if (deptIds == null) {
            return;
        }
        for (Long deptId : deptIds) {
            SysRoleDept relation = new SysRoleDept();
            relation.setRoleId(roleId);
            relation.setDeptId(deptId);
            roleDeptMapper.insert(relation);
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "状态只能是 0 或 1");
        }
    }

    private void validateDataScope(String dataScope) {
        if (dataScope != null && !DATA_SCOPES.contains(dataScope)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "数据范围只能是 self、dept、dept_tree、all、custom");
        }
    }

    private String dataScopeOrDefault(String dataScope) {
        return dataScope == null || dataScope.isBlank() ? "self" : dataScope;
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludeId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
    }

    private RoleResponse toResponse(SysRole role) {
        return new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDataScope(), role.getStatus(),
                menuIds(role.getId()), deptIds(role.getId()), role.getCreatedAt());
    }

    private List<Long> menuIds(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    private List<Long> deptIds(Long roleId) {
        return roleDeptMapper.selectList(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId))
                .stream().map(SysRoleDept::getDeptId).toList();
    }
}
