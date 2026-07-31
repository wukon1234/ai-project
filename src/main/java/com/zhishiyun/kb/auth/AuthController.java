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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) Map<String, String> payload) {
        authService.logout(payload == null ? null : payload.get("refreshToken"));
        return Result.ok(null);
    }

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

    /** SSO 回调：签发本地 JWT，并可跳转前端。 */
    @GetMapping("/sso/callback")
    public Result<AuthResponse> ssoCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {
        return Result.ok(authService.ssoCallback(code));
    }

    @PostMapping("/password/forgot")
    public Result<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return Result.ok(authService.forgotPassword(request));
    }

    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.ok(null);
    }
}
