package com.zhangjiyan.template.system.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.menu.dto.MenuRequest;
import com.zhangjiyan.template.system.menu.dto.MenuTreeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Menu", description = "菜单与按钮权限管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/menus")
public class SysMenuController {

    private final SysMenuMapper menuMapper;

    @Operation(summary = "菜单树查询")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:menu:view')")
    public ApiResult<List<MenuTreeResponse>> tree() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        return ApiResult.ok(tree(menus));
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:menu:create')")
    public ApiResult<Void> create(@Valid @RequestBody MenuRequest request) {
        validateStatus(request.visible(), "显示状态");
        validateStatus(request.status(), "启用状态");
        menuMapper.insert(toEntity(new SysMenu(), request));
        return ApiResult.ok();
    }

    @Operation(summary = "编辑菜单")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:menu:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        SysMenu menu = requireMenu(id);
        validateStatus(request.visible(), "显示状态");
        validateStatus(request.status(), "启用状态");
        menuMapper.updateById(toEntity(menu, request));
        return ApiResult.ok();
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:menu:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        requireMenu(id);
        Long children = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (children > 0) {
            throw new BusinessException("请先删除下级菜单");
        }
        menuMapper.deleteById(id);
        return ApiResult.ok();
    }

    @Operation(summary = "菜单排序")
    @PatchMapping("/{id}/sort")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:menu:update')")
    public ApiResult<Void> sort(@PathVariable Long id, @RequestParam Integer sort) {
        SysMenu menu = requireMenu(id);
        menu.setSort(sort);
        menuMapper.updateById(menu);
        return ApiResult.ok();
    }

    private SysMenu requireMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    private void validateStatus(Integer value, String fieldName) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, fieldName + "只能是 0 或 1");
        }
    }

    private SysMenu toEntity(SysMenu menu, MenuRequest request) {
        menu.setParentId(request.parentId());
        menu.setName(request.name());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setIcon(request.icon());
        menu.setType(request.type());
        menu.setPermission(request.permission());
        menu.setSort(request.sort() == null ? 0 : request.sort());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
        menu.setStatus(request.status() == null ? 1 : request.status());
        return menu;
    }

    private List<MenuTreeResponse> tree(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> byParent = menus.stream().collect(Collectors.groupingBy(SysMenu::getParentId));
        return children(0L, byParent);
    }

    private List<MenuTreeResponse> children(Long parentId, Map<Long, List<SysMenu>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Integer::compareTo)))
                .map(menu -> MenuTreeResponse.leaf(menu.getId(), menu.getParentId(), menu.getName(), menu.getPath(), menu.getComponent(),
                        menu.getIcon(), menu.getType(), menu.getPermission(), menu.getSort(), menu.getVisible(), menu.getStatus())
                        .withChildren(children(menu.getId(), byParent)))
                .toList();
    }
}
