package com.zhangjiyan.template.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.AuthConstants;
import com.zhangjiyan.template.common.security.JwtUtils;
import com.zhangjiyan.template.common.security.LoginUser;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.auth.dto.LoginRequest;
import com.zhangjiyan.template.system.auth.dto.LoginResponse;
import com.zhangjiyan.template.system.auth.dto.UserInfoResponse;
import com.zhangjiyan.template.system.log.SysLoginLog;
import com.zhangjiyan.template.system.log.SysLoginLogMapper;
import com.zhangjiyan.template.system.menu.SysMenu;
import com.zhangjiyan.template.system.menu.SysMenuMapper;
import com.zhangjiyan.template.system.menu.dto.MenuTreeResponse;
import com.zhangjiyan.template.system.role.*;
import com.zhangjiyan.template.system.token.SysRefreshToken;
import com.zhangjiyan.template.system.token.SysRefreshTokenMapper;
import com.zhangjiyan.template.system.user.SysUser;
import com.zhangjiyan.template.system.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long REFRESH_TOKEN_DAYS = 7;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginBundle login(LoginRequest request, String ip, String userAgent) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordLogin(request.username(), ip, userAgent, "FAIL", "账号或密码错误");
            throw new BusinessException(ResultCode.AUTH_INVALID_CREDENTIALS, "账号或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            recordLogin(request.username(), ip, userAgent, "FAIL", "账号已停用");
            throw new BusinessException(ResultCode.USER_DISABLED, "账号已停用");
        }
        LoginResponse response = buildLoginResponse(user);
        String rawRefreshToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        SysRefreshToken refreshToken = new SysRefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(Boolean.TRUE.equals(request.rememberMe()) ? 14 : REFRESH_TOKEN_DAYS));
        refreshToken.setRevoked(0);
        refreshTokenMapper.insert(refreshToken);
        recordLogin(user.getUsername(), ip, userAgent, "SUCCESS", "登录成功");
        return new LoginBundle(response, rawRefreshToken);
    }

    public LoginResponse currentUser() {
        LoginUser loginUser = SecurityUtils.currentUser()
                .orElseThrow(() -> new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "请先登录"));
        SysUser user = userMapper.selectById(loginUser.userId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "登录状态不可用");
        }
        return buildLoginResponse(user);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "刷新令牌不存在");
        }
        SysRefreshToken token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getTokenHash, hash(rawRefreshToken))
                .eq(SysRefreshToken::getRevoked, 0)
                .gt(SysRefreshToken::getExpiresAt, LocalDateTime.now()));
        if (token == null) {
            throw new BusinessException(ResultCode.AUTH_TOKEN_EXPIRED, "刷新令牌已过期");
        }
        SysUser user = userMapper.selectById(token.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "登录状态不可用");
        }
        return buildLoginResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        SysRefreshToken token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getTokenHash, hash(rawRefreshToken))
                .eq(SysRefreshToken::getRevoked, 0));
        if (token != null) {
            token.setRevoked(1);
            refreshTokenMapper.updateById(token);
        }
    }

    private LoginResponse buildLoginResponse(SysUser user) {
        List<SysRole> roles = rolesByUserId(user.getId());
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<Long> menuIds = roleIds.isEmpty() ? List.of() : roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .in(SysRoleMenu::getRoleId, roleIds)).stream().map(SysRoleMenu::getMenuId).toList();
        List<SysMenu> menus = menuIds.isEmpty() ? List.of() : menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSort));
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        List<String> permissions = menus.stream()
                .map(SysMenu::getPermission)
                .filter(Objects::nonNull)
                .filter(permission -> !permission.isBlank())
                .distinct()
                .toList();
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), roleCodes, permissions);
        return new LoginResponse(
                JwtUtils.createAccessToken(loginUser),
                JwtUtils.accessTokenExpiresInSeconds(),
                AuthConstants.TOKEN_PREFIX.trim(),
                new UserInfoResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getMobile(), user.getStatus()),
                roleCodes,
                permissions,
                buildMenuTree(menus.stream().filter(menu -> !"button".equals(menu.getType())).toList())
        );
    }

    private List<SysRole> rolesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 1));
    }

    private List<MenuTreeResponse> buildMenuTree(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> byParent = menus.stream().collect(Collectors.groupingBy(SysMenu::getParentId));
        return childrenOf(0L, byParent);
    }

    private List<MenuTreeResponse> childrenOf(Long parentId, Map<Long, List<SysMenu>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Integer::compareTo)))
                .map(menu -> MenuTreeResponse.leaf(
                        menu.getId(), menu.getParentId(), menu.getName(), menu.getPath(), menu.getComponent(),
                        menu.getIcon(), menu.getType(), menu.getPermission(), menu.getSort(), menu.getVisible(), menu.getStatus()
                ).withChildren(childrenOf(menu.getId(), byParent)))
                .toList();
    }

    private void recordLogin(String username, String ip, String userAgent, String status, String message) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername(username);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setStatus(status);
        log.setMessage(message);
        loginLogMapper.insert(log);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("refresh token hash failed", ex);
        }
    }

    public record LoginBundle(LoginResponse response, String refreshToken) {
    }
}
