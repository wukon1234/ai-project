package com.zhishiyun.kb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.Result;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 用户级问答限流：默认每分钟 10 次，覆盖会话流式问答与同文档问答。
 */
@Component
@RequiredArgsConstructor
public class AskRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kb.rate-limit.ask-per-minute:10}")
    private int askPerMinute;
    @Value("${kb.rate-limit.enabled:true}")
    private boolean enabled;

    /** 仅对会话流式问答与同文档问答路径生效。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path != null && (path.contains("/messages:stream") || path.contains("/ask:stream")));
    }

    /** Redis 滑动分钟窗口计数；超限返回 429。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long userId = ((AuthUser) auth.getPrincipal()).getUserId();
        String key = "rate:ask:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        if (count != null && count > askPerMinute) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(ErrorCode.RATE_LIMITED.getCode(), ErrorCode.RATE_LIMITED.getDefaultMessage())));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
