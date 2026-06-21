package com.zhangjiyan.template.system.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhangjiyan.template.common.dto.PageRequest;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.org.SysDept;
import com.zhangjiyan.template.system.org.SysDeptMapper;
import com.zhangjiyan.template.system.org.SysPost;
import com.zhangjiyan.template.system.org.SysPostMapper;
import com.zhangjiyan.template.system.role.SysRole;
import com.zhangjiyan.template.system.role.SysRoleMapper;
import com.zhangjiyan.template.system.role.SysUserRole;
import com.zhangjiyan.template.system.role.SysUserRoleMapper;
import com.zhangjiyan.template.system.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserResponse> pageUsers(PageRequest request) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(request.keyword() != null && !request.keyword().isBlank(), query -> query
                        .like(SysUser::getUsername, request.keyword())
                        .or()
                        .like(SysUser::getNickname, request.keyword()))
                .eq(request.status() != null, SysUser::getStatus, request.status())
                .eq(request.deptId() != null, SysUser::getDeptId, request.deptId())
                .eq(request.postId() != null, SysUser::getPostId, request.postId())
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> page = baseMapper.selectPage(Page.of(request.pageNum(), request.pageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toResponse).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public UserResponse detail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toResponse(user);
    }

    @Override
    @Transactional
    public void createUser(UserCreateRequest request) {
        validateStatus(request.status());
        if (baseMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username())) > 0) {
            throw new BusinessException("账号已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setMobile(request.mobile());
        user.setDeptId(request.deptId());
        user.setPostId(request.postId());
        user.setStatus(request.status() == null ? 1 : request.status());
        save(user);
        assignRoles(user.getId(), request.roleIds());
    }

    @Override
    @Transactional
    public void updateUser(Long id, UserUpdateRequest request) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        validateStatus(request.status());
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setMobile(request.mobile());
        user.setDeptId(request.deptId());
        user.setPostId(request.postId());
        user.setStatus(request.status() == null ? user.getStatus() : request.status());
        updateById(user);
        assignRoles(id, request.roleIds());
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        validateStatus(status);
        user.setStatus(status);
        updateById(user);
    }

    @Override
    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        if (getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        userRoleMapper.deletePhysicalByUserId(id);
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            if (roleMapper.selectById(roleId) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在: " + roleId);
            }
            SysUserRole relation = new SysUserRole();
            relation.setUserId(id);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "状态只能是 0 或 1");
        }
    }

    @Override
    public void changeCurrentPassword(PasswordRequest request) {
        Long currentUserId = SecurityUtils.currentUser().orElseThrow(() -> new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "请先登录")).userId();
        SysUser user = getById(currentUserId);
        if (user == null || !passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.AUTH_INVALID_CREDENTIALS, "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        updateById(user);
    }

    private UserResponse toResponse(SysUser user) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))
                .stream().map(SysRole::getRoleCode).toList();
        SysDept dept = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        SysPost post = user.getPostId() == null ? null : postMapper.selectById(user.getPostId());
        return new UserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getMobile(),
                user.getDeptId(), dept == null ? null : dept.getDeptName(), user.getPostId(), post == null ? null : post.getPostName(),
                user.getStatus(), roleIds, roles, user.getCreatedAt());
    }
}
