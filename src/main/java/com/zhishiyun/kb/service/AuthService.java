package com.zhishiyun.kb.service;


import com.zhishiyun.kb.config.JwtProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.dto.AuthResponse;
import com.zhishiyun.kb.dto.ForgotPasswordRequest;
import com.zhishiyun.kb.dto.LoginRequest;
import com.zhishiyun.kb.dto.RefreshRequest;
import com.zhishiyun.kb.dto.RegisterRequest;
import com.zhishiyun.kb.dto.ResetPasswordRequest;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.KbAclEntity;
import com.zhishiyun.kb.entity.SysRefreshTokenEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.entity.UserPreferenceEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import com.zhishiyun.kb.mapper.SysRefreshTokenMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.mapper.UserPreferenceMapper;
import io.jsonwebtoken.Claims;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 账号鉴权：登录/注册/刷新/登出、SSO 与密码重置。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PWD_RESET_PREFIX = "pwd:reset:";

    private final SysUserMapper sysUserMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final KbAclMapper kbAclMapper;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    @Value("${auth.register.auto-approve:true}")
    private boolean autoApprove;
    @Value("${auth.register.company-email-suffixes:@company.com}")
    private String companyEmailSuffixes;

    @Value("${kb.sso.enabled:false}")
    private boolean ssoEnabled;
    @Value("${kb.sso.mock-enabled:true}")
    private boolean ssoMockEnabled;
    @Value("${kb.sso.client-id:}")
    private String ssoClientId;
    @Value("${kb.sso.tenant:common}")
    private String ssoTenant;
    @Value("${kb.sso.redirect-uri:http://localhost:8080/api/v1/auth/sso/callback}")
    private String ssoRedirectUri;
    @Value("${kb.sso.frontend-redirect:http://localhost:5173/auth/callback}")
    private String ssoFrontendRedirect;

    /** 邮箱或手机号登录，支持 rememberMe 延长 refresh 有效期。 */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String account = request.getAccount() == null ? "" : request.getAccount().trim();
        log.debug("login query by account={}", account);
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .and(w -> w.eq(SysUserEntity::getEmail, account).or().eq(SysUserEntity::getMobile, account))
                .last("limit 1"));
        if (user == null) {
            log.warn("login failed: user not found, account={}", account);
            throw new BizException(ErrorCode.BIZ_ERROR, "账号或密码错误");
        }
        boolean pwdOk = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        log.debug("login user id={} role={} status={} pwdOk={}",
                user.getId(), user.getRoleCode(), user.getStatus(), pwdOk);
        if (!pwdOk) {
            log.warn("login failed: bad password, userId={} account={}", user.getId(), account);
            throw new BizException(ErrorCode.BIZ_ERROR, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("login failed: status unavailable, userId={} status={}", user.getId(), user.getStatus());
            throw new BizException(ErrorCode.BIZ_ERROR, "账号不可用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        AuthResponse response = buildTokens(user, Boolean.TRUE.equals(request.getRememberMe()));
        auditService.write(user.getId(), "LOGIN");
        return response;
    }

    /** 企业邮箱注册；可配置自动审批，并初始化默认偏好与角色。 */
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

        // 默认可读 hr/product，与 preference 保持一致
        for (String code : new String[] {"hr", "product"}) {
            KbAclEntity acl = new KbAclEntity();
            acl.setUserId(user.getId());
            acl.setLibraryCode(code);
            acl.setLibraryId("hr".equals(code) ? 2L : 1L);
            acl.setPerm("READ");
            acl.setCreatedAt(LocalDateTime.now());
            kbAclMapper.insert(acl);
        }

        if (!autoApprove) {
            throw new BizException(ErrorCode.BIZ_ERROR, "注册成功，待审核后可登录");
        }
        return buildTokens(user, false);
    }

    /** 校验并轮换 refresh token，签发新的 access/refresh。 */
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

    /** 吊销 refresh token（幂等）。 */
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

    /** 当前登录用户资料（不含 token）。 */
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

    /** 签发 access JWT，并将 refresh 的 SHA-256 哈希落库。 */
    private AuthResponse buildTokens(SysUserEntity user, boolean rememberMe) {
        String accessToken = jwtService.createAccessToken(user.getId(), user.getRoleCode());
        String refreshToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        SysRefreshTokenEntity tokenEntity = new SysRefreshTokenEntity();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenHash(sha256(refreshToken));
        tokenEntity.setRememberMe(rememberMe ? 1 : 0);
        tokenEntity.setRevoked(0);
        tokenEntity.setCreatedAt(LocalDateTime.now());
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
        auditService.write(userId, action);
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

    /**
     * 构造 Azure AD 授权 URL；无真实租户时 mock 模式直接指向本地 callback。
     */
    public String buildSsoAuthorizeUrl() {
        if (ssoMockEnabled || !ssoEnabled || !StringUtils.hasText(ssoClientId)) {
            return "/api/v1/auth/sso/callback?code=mock-sso-code&state=dev";
        }
        try {
            String redirect = URLEncoder.encode(ssoRedirectUri, "UTF-8");
            return "https://login.microsoftonline.com/" + ssoTenant
                    + "/oauth2/v2.0/authorize?client_id=" + ssoClientId
                    + "&response_type=code&redirect_uri=" + redirect
                    + "&response_mode=query&scope=openid%20profile%20email&state=zhishiyun";
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "构造 SSO URL 失败");
        }
    }

    /**
     * SSO 回调：mock 或真实 code 换用户，绑定 sso_subject 后签发本地 JWT。
     */
    @Transactional
    public AuthResponse ssoCallback(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 authorization code");
        }
        String subject;
        String email;
        String name;
        if (ssoMockEnabled || "mock-sso-code".equals(code)) {
            subject = "mock-oid-zhangming";
            email = "zhangming@company.com";
            name = "张明(SSO)";
        } else {
            // 真实环境应调用 Azure token endpoint；此处保留扩展点
            throw new BizException(ErrorCode.BIZ_ERROR, "未开启 mock 且未配置真实 SSO token 交换");
        }
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getSsoSubject, subject)
                .last("limit 1"));
        if (user == null) {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                    .eq(SysUserEntity::getEmail, email)
                    .last("limit 1"));
        }
        if (user == null) {
            user = new SysUserEntity();
            user.setName(name);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRoleCode("EMPLOYEE");
            user.setDeptName("SSO");
            user.setEmpNo("SSO");
            user.setStatus(1);
            user.setSsoSubject(subject);
            sysUserMapper.insert(user);
            UserPreferenceEntity preference = new UserPreferenceEntity();
            preference.setUserId(user.getId());
            preference.setThemeMode("system");
            preference.setNotifyKbUpdate(1);
            preference.setNotifyMention(1);
            preference.setDefaultKbScopes("[\"hr\",\"product\"]");
            userPreferenceMapper.insert(preference);
        } else if (!StringUtils.hasText(user.getSsoSubject())) {
            user.setSsoSubject(subject);
            sysUserMapper.updateById(user);
        }
        writeAudit(user.getId(), "SSO_LOGIN");
        return buildTokens(user, false);
    }

    public String getSsoFrontendRedirect() {
        return ssoFrontendRedirect;
    }

    /**
     * 忘记密码：生成一次性 token 写入 Redis（30 分钟），邮件发送 stub 为日志输出。
     */
    public Map<String, Object> forgotPassword(ForgotPasswordRequest request) {
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getEmail, request.getEmail())
                .last("limit 1"));
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("accepted", true);
        // 防枚举：即使用户不存在也返回成功
        if (user == null) {
            return data;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PWD_RESET_PREFIX + token, String.valueOf(user.getId()), 30, TimeUnit.MINUTES);
        String resetLink = "/api/v1/auth/password/reset?token=" + token;
        log.info("[password-reset] email={} resetLink={}", request.getEmail(), resetLink);
        data.put("devResetToken", token);
        return data;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String userId = redisTemplate.opsForValue().get(PWD_RESET_PREFIX + request.getToken());
        if (!StringUtils.hasText(userId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "重置令牌无效或已过期");
        }
        SysUserEntity user = sysUserMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(user);
        redisTemplate.delete(PWD_RESET_PREFIX + request.getToken());
        writeAudit(user.getId(), "PASSWORD_RESET");
    }
}
