package com.zhishiyun.kb.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.model.AuthUser;
import com.zhishiyun.kb.common.ErrorCode;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
        AskRateLimitFilter filter = new AskRateLimitFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "askPerMinute", 10);
        ReflectionTestUtils.setField(filter, "enabled", true);

        AuthUser user = new AuthUser(1001L, "EMPLOYEE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));

        // 预填 11 个当前时间戳，模拟一分钟内已请求 11 次
        long now = System.currentTimeMillis();
        Deque<Long> deque = new ArrayDeque<Long>();
        for (int i = 0; i < 11; i++) {
            deque.addLast(now);
        }
        Map<Long, Deque<Long>> windows = new ConcurrentHashMap<Long, Deque<Long>>();
        windows.put(1001L, deque);
        ReflectionTestUtils.setField(filter, "windows", windows);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/chat/sessions/1/messages:stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(429, response.getStatus());
        Assertions.assertTrue(response.getContentAsString().contains(String.valueOf(ErrorCode.RATE_LIMITED.getCode())));
        SecurityContextHolder.clearContext();
    }
}
