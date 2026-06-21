package com.zhangjiyan.template.system.auth;

import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.security.AuthConstants;
import com.zhangjiyan.template.common.web.ClientInfoUtils;
import com.zhangjiyan.template.system.auth.dto.LoginRequest;
import com.zhangjiyan.template.system.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "登录、当前用户、刷新令牌和退出")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        AuthService.LoginBundle bundle = authService.login(request, ClientInfoUtils.ip(httpRequest), ClientInfoUtils.userAgent(httpRequest));
        writeRefreshCookie(httpResponse, bundle.refreshToken(), Boolean.TRUE.equals(request.rememberMe()) ? 14 * 24 * 3600 : 7 * 24 * 3600);
        return ApiResult.ok(bundle.response());
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResult<LoginResponse> me() {
        return ApiResult.ok(authService.currentUser());
    }

    @Operation(summary = "刷新 access token")
    @PostMapping("/refresh")
    public ApiResult<LoginResponse> refresh(@CookieValue(name = AuthConstants.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        return ApiResult.ok(authService.refresh(refreshToken));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResult<Void> logout(@CookieValue(name = AuthConstants.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                  HttpServletResponse response) {
        authService.logout(refreshToken);
        writeRefreshCookie(response, "", 0);
        return ApiResult.ok();
    }

    private void writeRefreshCookie(HttpServletResponse response, String token, int maxAge) {
        Cookie cookie = new Cookie(AuthConstants.REFRESH_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(maxAge);
        cookie.setSecure(false);
        response.addCookie(cookie);
    }
}
