package com.zhangjiyan.template.system.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhangjiyan.template.system.dict.SysDictData;
import com.zhangjiyan.template.system.dict.SysDictDataMapper;
import com.zhangjiyan.template.system.log.SysLoginLog;
import com.zhangjiyan.template.system.log.SysLoginLogMapper;
import com.zhangjiyan.template.system.log.SysOperationLog;
import com.zhangjiyan.template.system.log.SysOperationLogMapper;
import com.zhangjiyan.template.system.menu.SysMenu;
import com.zhangjiyan.template.system.menu.SysMenuMapper;
import com.zhangjiyan.template.system.notice.SysNotice;
import com.zhangjiyan.template.system.notice.SysNoticeMapper;
import com.zhangjiyan.template.system.role.SysRole;
import com.zhangjiyan.template.system.role.SysRoleMapper;
import com.zhangjiyan.template.system.user.SysUser;
import com.zhangjiyan.template.system.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysDictDataMapper dictDataMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysOperationLogMapper operationLogMapper;
    private final SysNoticeMapper noticeMapper;

    public OverviewResponse overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new OverviewResponse(
                userMapper.selectCount(new LambdaQueryWrapper<SysUser>()),
                roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()),
                menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()),
                dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()),
                noticeMapper.selectCount(new LambdaQueryWrapper<SysNotice>().eq(SysNotice::getStatus, 1)),
                loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>().ge(SysLoginLog::getCreatedAt, todayStart)),
                operationLogMapper.selectList(new LambdaQueryWrapper<SysOperationLog>().orderByDesc(SysOperationLog::getCreatedAt).last("LIMIT 8"))
        );
    }

    public List<LoginItem> recentLogins() {
        return loginLogMapper.selectList(new LambdaQueryWrapper<SysLoginLog>().orderByDesc(SysLoginLog::getCreatedAt).last("LIMIT 8"))
                .stream().map(log -> new LoginItem(log.getUsername(), log.getIp(), log.getStatus(), log.getMessage(), log.getCreatedAt())).toList();
    }

    public List<TrendItem> operationTrend() {
        List<TrendItem> items = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Long count = operationLogMapper.selectCount(new QueryWrapper<SysOperationLog>()
                    .ge("created_at", day.atStartOfDay())
                    .lt("created_at", day.plusDays(1).atStartOfDay()));
            items.add(new TrendItem(day.toString(), count));
        }
        return items;
    }

    public List<ModuleItem> systemModules() {
        return List.of(
                new ModuleItem("认证中心", "登录、刷新令牌、退出、当前用户恢复", "RUNNING"),
                new ModuleItem("RBAC 权限", "用户、角色、菜单、按钮权限基础闭环", "RUNNING"),
                new ModuleItem("组织架构", "部门树、岗位维护、用户归属筛选", "RUNNING"),
                new ModuleItem("基础数据", "字典类型、字典数据、系统菜单、系统参数", "RUNNING"),
                new ModuleItem("通知公告", "公告发布、有效期、置顶和首页读取", "RUNNING"),
                new ModuleItem("审计日志", "登录日志和关键操作日志", "RUNNING")
        );
    }

    public record OverviewResponse(Long userCount, Long roleCount, Long menuCount, Long dictItemCount, Long noticeCount,
                                   Long todayLoginCount, List<SysOperationLog> recentOperations) {
    }

    public record LoginItem(String username, String ip, String status, String message, LocalDateTime createdAt) {
    }

    public record TrendItem(String date, Long count) {
    }

    public record ModuleItem(String name, String description, String status) {
    }
}
