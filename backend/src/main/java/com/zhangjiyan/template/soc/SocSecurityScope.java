package com.zhangjiyan.template.soc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.zhangjiyan.template.common.security.LoginUser;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.org.SysDept;
import com.zhangjiyan.template.system.org.SysDeptMapper;
import com.zhangjiyan.template.system.role.*;
import com.zhangjiyan.template.system.user.SysUser;
import com.zhangjiyan.template.system.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SocSecurityScope {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    public boolean canViewAllData() {
        return currentScope().allowAll();
    }

    public <T> void applyDataScope(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> ownerColumn, SFunction<T, Long> deptColumn) {
        DataScope scope = currentScope();
        if (scope.allowAll()) {
            Long ownerId = requestedOwnerId();
            if (ownerId != null) {
                wrapper.eq(ownerColumn, ownerId);
            }
            return;
        }
        if (scope.userId() == null) {
            wrapper.eq(ownerColumn, -1L);
            return;
        }
        wrapper.and(w -> {
            boolean hasCondition = false;
            if (scope.includeSelf()) {
                w.eq(ownerColumn, scope.userId());
                hasCondition = true;
            }
            if (!scope.deptIds().isEmpty()) {
                if (hasCondition) {
                    w.or();
                }
                w.in(deptColumn, scope.deptIds());
                hasCondition = true;
            }
            if (!hasCondition) {
                w.eq(ownerColumn, scope.userId());
            }
        });
    }

    public boolean canAccess(Long ownerId, Long deptId) {
        DataScope scope = currentScope();
        boolean allowed;
        if (scope.allowAll()) {
            allowed = true;
        } else if (scope.includeSelf() && ownerId != null && ownerId.equals(scope.userId())) {
            allowed = true;
        } else {
            allowed = deptId != null && scope.deptIds().contains(deptId);
        }
        Long selectedOwner = requestedOwnerId();
        return allowed && (selectedOwner == null || selectedOwner.equals(ownerId));
    }

    /** Applies both the role scope and the optional operator-selected user workspace. */
    public boolean canAccessSelectedWorkspace(Long ownerId, Long deptId) {
        Long selectedOwner = requestedOwnerId();
        return canAccess(ownerId, deptId) && (selectedOwner == null || selectedOwner.equals(ownerId));
    }

    public Long currentUserId() {
        return SecurityUtils.currentUser().map(LoginUser::userId).orElse(null);
    }

    public Long currentDeptId() {
        Long userId = currentUserId();
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    /** The selected user workspace takes precedence over the signed-in operator for newly created scoped records. */
    public Long activeOwnerId() {
        Long selectedOwner = requestedOwnerId();
        return selectedOwner == null ? currentUserId() : selectedOwner;
    }

    public Long activeDeptId() {
        Long ownerId = activeOwnerId();
        if (ownerId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(ownerId);
        return user == null ? null : user.getDeptId();
    }

    public String currentUsername() {
        return SecurityUtils.currentUsername();
    }

    /**
     * A platform operator may enter a user's workspace from the aggregate card.
     * The requested owner is intentionally ignored for every non-global scope.
     */
    private Long requestedOwnerId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        String value = attributes.getRequest().getParameter("ownerId");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long ownerId = Long.parseLong(value.trim());
            return ownerId > 0 ? ownerId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private DataScope currentScope() {
        Long userId = currentUserId();
        if (userId == null) {
            return new DataScope(null, false, true, Set.of());
        }
        SysUser user = userMapper.selectById(userId);
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return new DataScope(userId, false, true, Set.of());
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds).eq(SysRole::getStatus, 1));
        if (roles.stream().anyMatch(role -> "all".equals(role.getDataScope()))) {
            return new DataScope(userId, true, false, Set.of());
        }

        Set<Long> deptIds = new LinkedHashSet<>();
        boolean includeSelf = false;
        Long userDeptId = user == null ? null : user.getDeptId();
        for (SysRole role : roles) {
            switch (role.getDataScope() == null ? "self" : role.getDataScope()) {
                case "dept" -> addIfPresent(deptIds, userDeptId);
                case "dept_tree" -> addDeptAndChildren(deptIds, userDeptId);
                case "custom" -> deptIds.addAll(roleDeptMapper.selectList(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, role.getId()))
                        .stream().map(SysRoleDept::getDeptId).toList());
                default -> includeSelf = true;
            }
        }
        return new DataScope(userId, false, includeSelf, deptIds);
    }

    private void addDeptAndChildren(Set<Long> result, Long deptId) {
        if (deptId == null || result.contains(deptId)) {
            return;
        }
        result.add(deptId);
        List<SysDept> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, deptId));
        for (SysDept child : children) {
            addDeptAndChildren(result, child.getId());
        }
    }

    private void addIfPresent(Set<Long> result, Long value) {
        if (value != null) {
            result.add(value);
        }
    }

    private record DataScope(Long userId, boolean allowAll, boolean includeSelf, Set<Long> deptIds) {
    }
}
