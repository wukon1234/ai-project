package com.zhishiyun.kb.chat;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.chat.dto.BatchDeleteRequest;
import com.zhishiyun.kb.chat.dto.CreateSessionRequest;
import com.zhishiyun.kb.chat.dto.UpdateScopeRequest;
import com.zhishiyun.kb.common.Result;
import com.zhishiyun.kb.share.ShareService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 对话会话 API。 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ShareService shareService;

    /** 创建会话。 */
    @PostMapping
    public Result<?> create(Authentication auth, @RequestBody(required = false) CreateSessionRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.create(user.getUserId(), request == null ? null : request.getScope()));
    }

    /** 会话列表，支持标题/最近问题关键词过滤。 */
    @GetMapping
    public Result<?> list(Authentication auth, @RequestParam(value = "keyword", required = false) String keyword) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.list(user.getUserId(), keyword));
    }

    /** 会话详情（消息 + 引用）。 */
    @GetMapping("/{id}")
    public Result<?> detail(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.detail(user.getUserId(), id));
    }

    /** 软删除单个会话。 */
    @DeleteMapping("/{id}")
    public Result<?> delete(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        chatSessionService.delete(user.getUserId(), id);
        return Result.ok(null);
    }

    /** 批量软删除会话。 */
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(Authentication auth, @Valid @RequestBody BatchDeleteRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        chatSessionService.batchDelete(user.getUserId(), request.getIds());
        return Result.ok(null);
    }

    /** 切换会话检索知识库范围。 */
    @PatchMapping("/{id}/scope")
    public Result<?> patchScope(Authentication auth, @PathVariable Long id, @Valid @RequestBody UpdateScopeRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.patchScope(user.getUserId(), id, request.getScope()));
    }

    /** 清空会话消息，保留会话壳。 */
    @PostMapping("/{id}/clear")
    public Result<?> clear(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        chatSessionService.clear(user.getUserId(), id);
        return Result.ok(null);
    }

    /** 生成会话分享短链。 */
    @PostMapping("/{id}/share")
    public Result<?> share(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(shareService.shareSession(user.getUserId(), id));
    }
}
