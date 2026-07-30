package com.zhishiyun.kb.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.auth.dto.AuthResponse;
import com.zhishiyun.kb.auth.dto.LoginRequest;
import com.zhishiyun.kb.auth.dto.RefreshRequest;
import com.zhishiyun.kb.auth.dto.RegisterRequest;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.infra.mysql.entity.AuditLogEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbAclEntity;
import com.zhishiyun.kb.infra.mysql.entity.SysRefreshTokenEntity;
import com.zhishiyun.kb.infra.mysql.entity.SysUserEntity;
import com.zhishiyun.kb.infra.mysql.entity.UserPreferenceEntity;
import com.zhishiyun.kb.infra.mysql.mapper.AuditLogMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbAclMapper;
import com.zhishiyun.kb.infra.mysql.mapper.SysRefreshTokenMapper;
import com.zhishiyun.kb.infra.mysql.mapper.SysUserMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UserPreferenceMapper;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final KbAclMapper kbAclMapper;
    private final AuditLogMapper auditLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Value("${auth.register.auto-approve:true}")
    private boolean autoApprove;
    @Value("${auth.register.company-email-suffixes:@company.com}")
    private String companyEmailSuffixes;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getEmail, request.getAccount())
                .or()
                .eq(SysUserEntity::getMobile, request.getAccount())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "账号不可用");
        }
        AuthResponse response = buildTokens(user, Boolean.TRUE.equals(request.getRememberMe()));
        writeAudit(user.getId(), "LOGIN");
        return response;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!isEmailAllowed(request.getEmail())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "邮箱后缀不允许");
        }
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getEmail, request.getEmail())) > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "邮箱已注册");
        }
        SysUserEntity user = new SysUserEntity();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoleCode("EMPLOYEE");
        user.setDeptName("待分配");
        user.setEmpNo("PENDING");
        user.setStatus(autoApprove ? 1 : 0);
        sysUserMapper.insert(user);

        UserPreferenceEntity preference = new UserPreferenceEntity();
        preference.setUserId(user.getId());
        preference.setThemeMode("system");
        preference.setNotifyKbUpdate(1);
        preference.setNotifyMention(1);
        preference.setDefaultKbScopes("[\"hr\",\"product\"]");
        userPreferenceMapper.insert(preference);

        if (!autoApprove) {
            throw new BizException(ErrorCode.BIZ_ERROR, "注册成功，待审核后可登录");
        }
        return buildTokens(user, false);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256(request.getRefreshToken());
        SysRefreshTokenEntity token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshTokenEntity>()
                .eq(SysRefreshTokenEntity::getTokenHash, hash)
                .last("limit 1"));
        if (token == null || token.getRevoked() == 1 || token.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        SysUserEntity user = sysUserMapper.selectById(token.getUserId());
        if (user == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        token.setRevoked(1);
        refreshTokenMapper.updateById(token);
        return buildTokens(user, token.getRememberMe() == 1);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        String hash = sha256(refreshToken);
        SysRefreshTokenEntity token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshTokenEntity>()
                .eq(SysRefreshTokenEntity::getTokenHash, hash)
                .last("limit 1"));
        if (token != null) {
            token.setRevoked(1);
            refreshTokenMapper.updateById(token);
        }
    }

    public AuthResponse me(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return AuthResponse.builder()
                .user(toUserInfo(user))
                .expiresIn((long) jwtProperties.getAccessTokenMinutes() * 60)
                .build();
    }

    private AuthResponse buildTokens(SysUserEntity user, boolean rememberMe) {
        String accessToken = jwtService.createAccessToken(user.getId(), user.getRoleCode());
        String refreshToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        SysRefreshTokenEntity tokenEntity = new SysRefreshTokenEntity();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenHash(sha256(refreshToken));
        tokenEntity.setRememberMe(rememberMe ? 1 : 0);
        tokenEntity.setRevoked(0);
        tokenEntity.setExpireAt(LocalDateTime.now().plusDays(rememberMe
                ? jwtProperties.getRefreshTokenRememberDays()
                : jwtProperties.getRefreshTokenDays()));
        refreshTokenMapper.insert(tokenEntity);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn((long) jwtProperties.getAccessTokenMinutes() * 60)
                .user(toUserInfo(user))
                .build();
    }

    private AuthResponse.UserInfo toUserInfo(SysUserEntity user) {
        List<String> scopes = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>()
                        .eq(KbAclEntity::getUserId, user.getId()))
                .stream()
                .map(KbAclEntity::getLibraryCode)
                .distinct()
                .collect(Collectors.toList());
        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .deptName(user.getDeptName())
                .empNo(user.getEmpNo())
                .roleCode(user.getRoleCode())
                .defaultKbScopes(scopes)
                .build();
    }

    private void writeAudit(Long userId, String action) {
        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType("auth");
        log.setTargetId(String.valueOf(userId));
        log.setDetail(action);
        auditLogMapper.insert(log);
    }

    private String sha256(String raw) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Token hash failed");
        }
    }

    private boolean isEmailAllowed(String email) {
        if (!StringUtils.hasText(companyEmailSuffixes)) {
            return true;
        }
        return Arrays.stream(companyEmailSuffixes.split(","))
                .map(String::trim)
                .anyMatch(email::endsWith);
    }

    public Long parseUserId(String accessToken) {
        Claims claims = jwtService.parse(accessToken);
        return Long.valueOf(claims.getSubject());
    }
}
