package com.zhishiyun.kb.chat;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.chat.dto.BatchDeleteRequest;
import com.zhishiyun.kb.chat.dto.CreateSessionRequest;
import com.zhishiyun.kb.chat.dto.UpdateScopeRequest;
import com.zhishiyun.kb.common.Result;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping
    public Result<?> create(Authentication auth, @RequestBody(required = false) CreateSessionRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.create(user.getUserId(), request == null ? null : request.getScope()));
    }

    @GetMapping
    public Result<?> list(Authentication auth, @RequestParam(value = "keyword", required = false) String keyword) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.list(user.getUserId(), keyword));
    }

    @GetMapping("/{id}")
    public Result<?> detail(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.detail(user.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        chatSessionService.delete(user.getUserId(), id);
        return Result.ok(null);
    }

    @PostMapping("/batch-delete")
    public Result<?> batchDelete(Authentication auth, @Valid @RequestBody BatchDeleteRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        chatSessionService.batchDelete(user.getUserId(), request.getIds());
        return Result.ok(null);
    }

    @PatchMapping("/{id}/scope")
    public Result<?> patchScope(Authentication auth, @PathVariable Long id, @Valid @RequestBody UpdateScopeRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(chatSessionService.patchScope(user.getUserId(), id, request.getScope()));
    }

    @PostMapping("/{id}/clear")
    public Result<?> clear(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        chatSessionService.clear(user.getUserId(), id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/share")
    public Result<?> share(Authentication auth, @PathVariable Long id) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        String shareUrl = chatSessionService.share(user.getUserId(), id);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("shareUrl", shareUrl);
        return Result.ok(data);
    }
}
