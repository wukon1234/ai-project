package com.zhishiyun.kb.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.model.AuthUser;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.Result;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 用户级问答限流（进程内实现）：默认每分钟 10 次，覆盖会话流式问答与同文档问答。
 * 注意：进程内计数仅对单实例部署有效，多实例需改回外部存储。
 */
@Component
@RequiredArgsConstructor
public class AskRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    /** 用户 -> 最近一分钟内的请求时间戳队列 */
    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<Long, Deque<Long>>();

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

    /** 进程内滑动分钟窗口计数；超限返回 429。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long userId = ((AuthUser) auth.getPrincipal()).getUserId();
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> deque = windows.computeIfAbsent(userId, k -> new ArrayDeque<Long>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            deque.addLast(now);
            if (deque.size() > askPerMinute) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(
                        Result.fail(ErrorCode.RATE_LIMITED.getCode(), ErrorCode.RATE_LIMITED.getDefaultMessage())));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
