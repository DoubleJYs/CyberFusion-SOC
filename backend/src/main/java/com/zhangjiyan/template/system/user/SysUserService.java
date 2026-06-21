package com.zhangjiyan.template.system.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhangjiyan.template.common.dto.PageRequest;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.system.user.dto.*;

import java.util.List;

public interface SysUserService extends IService<SysUser> {
    PageResult<UserResponse> pageUsers(PageRequest request);

    UserResponse detail(Long id);

    void createUser(UserCreateRequest request);

    void updateUser(Long id, UserUpdateRequest request);

    void changeStatus(Long id, Integer status);

    void resetPassword(Long id, ResetPasswordRequest request);

    void assignRoles(Long id, List<Long> roleIds);

    void changeCurrentPassword(PasswordRequest request);
}
