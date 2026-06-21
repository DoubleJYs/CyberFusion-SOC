package com.zhangjiyan.template.system.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deletePhysicalByRoleId(@Param("roleId") Long roleId);
}
