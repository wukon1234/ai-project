package com.zhishiyun.kb.service;


import com.zhishiyun.kb.config.JwtProperties;
import com.zhishiyun.kb.dto.AuthResponse;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import com.zhishiyun.kb.mapper.SysRefreshTokenMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.mapper.UserPreferenceMapper;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** SSO mock 回调应签发本地 Token。 */
class AuthSsoServiceTest {

    @Test
    void mockSsoCallbackShouldIssueToken() {
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        UserPreferenceMapper preferenceMapper = Mockito.mock(UserPreferenceMapper.class);
        SysRefreshTokenMapper refreshTokenMapper = Mockito.mock(SysRefreshTokenMapper.class);
        KbAclMapper aclMapper = Mockito.mock(KbAclMapper.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        JwtProperties props = new JwtProperties();
        props.setIssuer("test");
        props.setSecret("dev-secret-dev-secret-dev-secret");
        props.setAccessTokenMinutes(120);
        props.setRefreshTokenDays(7);
        props.setRefreshTokenRememberDays(30);
        JwtService jwtService = new JwtService(props);

        AuthService service = new AuthService(
                userMapper, preferenceMapper, refreshTokenMapper, aclMapper, auditService,
                new BCryptPasswordEncoder(), jwtService, props, redis);
        ReflectionTestUtils.setField(service, "ssoMockEnabled", true);

        SysUserEntity existing = new SysUserEntity();
        existing.setId(1001L);
        existing.setName("张明");
        existing.setEmail("zhangming@company.com");
        existing.setRoleCode("EMPLOYEE");
        existing.setStatus(1);
        Mockito.when(userMapper.selectOne(ArgumentMatchers.any())).thenReturn(existing);
        Mockito.when(aclMapper.selectList(ArgumentMatchers.any())).thenReturn(Collections.emptyList());

        AuthResponse resp = service.ssoCallback("mock-sso-code");
        Assertions.assertNotNull(resp.getAccessToken());
        Assertions.assertNotNull(resp.getRefreshToken());
    }
}
