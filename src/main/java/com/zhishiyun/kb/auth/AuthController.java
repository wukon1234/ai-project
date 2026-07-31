package com.zhishiyun.kb.auth;

import com.zhishiyun.kb.auth.dto.AuthResponse;
import com.zhishiyun.kb.auth.dto.ForgotPasswordRequest;
import com.zhishiyun.kb.auth.dto.LoginRequest;
import com.zhishiyun.kb.auth.dto.RefreshRequest;
import com.zhishiyun.kb.auth.dto.RegisterRequest;
import com.zhishiyun.kb.auth.dto.ResetPasswordRequest;
import com.zhishiyun.kb.common.Result;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 账号鉴权 API：登录注册、JWT 刷新、SSO、忘记密码。 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 邮箱/手机号登录。 */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 企业邮箱注册。 */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 刷新 access token。 */
    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    /** 登出：吊销 refresh token。 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) Map<String, String> payload) {
        authService.logout(payload == null ? null : payload.get("refreshToken"));
        return Result.ok(null);
    }

    /** 当前用户资料。 */
    @GetMapping("/me")
    public Result<AuthResponse> me(Authentication authentication) {
        AuthUser principal = (AuthUser) authentication.getPrincipal();
        return Result.ok(authService.me(principal.getUserId()));
    }

    /** SSO：重定向到 Azure AD（或 mock callback）。 */
    @GetMapping("/sso/authorize")
    public void ssoAuthorize(HttpServletResponse response) throws IOException {
        response.sendRedirect(authService.buildSsoAuthorizeUrl());
    }

    /** SSO 回调：签发本地 JWT，并重定向到前端携带 token。 */
    @GetMapping("/sso/callback")
    public void ssoCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletResponse response) throws IOException {
        AuthResponse auth = authService.ssoCallback(code);
        String base = authService.getSsoFrontendRedirect();
        if (base == null || base.trim().isEmpty()) {
            base = "http://localhost:5173/";
        }
        String sep = base.contains("?") ? "&" : "?";
        String access = auth.getAccessToken() == null ? "" : java.net.URLEncoder.encode(auth.getAccessToken(), "UTF-8");
        String refresh = auth.getRefreshToken() == null ? "" : java.net.URLEncoder.encode(auth.getRefreshToken(), "UTF-8");
        response.sendRedirect(base + sep + "accessToken=" + access + "&refreshToken=" + refresh + "&sso=1");
    }

    /** 发送重置密码令牌（防枚举，始终成功）。 */
    @PostMapping("/password/forgot")
    public Result<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return Result.ok(authService.forgotPassword(request));
    }

    /** 使用令牌重置密码。 */
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.ok(null);
    }
}
