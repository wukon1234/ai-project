package com.zhishiyun.kb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.ErrorCode;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** 问答限流单测：超过阈值返回 42901。 */
class AskRateLimitFilterTest {

    @Test
    void shouldBlockWhenExceedLimit() throws Exception {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        Mockito.when(redis.opsForValue()).thenReturn(ops);
        Mockito.when(ops.increment("rate:ask:1001")).thenReturn(11L);

        AskRateLimitFilter filter = new AskRateLimitFilter(redis, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "askPerMinute", 10);
        ReflectionTestUtils.setField(filter, "enabled", true);

        AuthUser user = new AuthUser(1001L, "EMPLOYEE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/chat/sessions/1/messages:stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(429, response.getStatus());
        Assertions.assertTrue(response.getContentAsString().contains(String.valueOf(ErrorCode.RATE_LIMITED.getCode())));
        SecurityContextHolder.clearContext();
    }
}
