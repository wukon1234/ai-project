package com.zhishiyun.kb.chat;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.chat.dto.AskRequest;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 对话消息 API：流式提问与重新生成。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatMessageController {

    private final ChatStreamService chatStreamService;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionService chatSessionService;

    /** SSE 流式提问。 */
    @PostMapping(value = "/sessions/{id}/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(Authentication auth, @PathVariable("id") Long sessionId, @Valid @RequestBody AskRequest request) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return chatStreamService.askStream(user.getUserId(), sessionId, request.getQuestion());
    }

    /** 基于原用户消息重新流式生成。 */
    @PostMapping(value = "/messages/{id}/regenerate:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(Authentication auth, @PathVariable("id") Long messageId) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        ChatMessageEntity message = chatMessageMapper.selectById(messageId);
        if (message == null || !"user".equals(message.getRole())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "messageId 无效");
        }
        chatSessionService.owned(user.getUserId(), message.getSessionId());
        return chatStreamService.askStream(user.getUserId(), message.getSessionId(), message.getContent());
    }
}
