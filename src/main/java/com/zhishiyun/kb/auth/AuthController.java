package com.zhishiyun.kb.auth;

import com.zhishiyun.kb.auth.dto.AuthResponse;
import com.zhishiyun.kb.auth.dto.LoginRequest;
import com.zhishiyun.kb.auth.dto.RefreshRequest;
import com.zhishiyun.kb.auth.dto.RegisterRequest;
import com.zhishiyun.kb.common.Result;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
