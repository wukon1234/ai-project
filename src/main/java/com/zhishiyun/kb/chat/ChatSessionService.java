package com.zhishiyun.kb.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.infra.mysql.entity.ChatCitationEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatSessionEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatCitationMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatSessionMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatCitationMapper chatCitationMapper;

    @Transactional
    public ChatSessionEntity create(Long userId, String scope) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUserId(userId);
        session.setScope((scope == null || scope.trim().isEmpty()) ? "hr" : scope);
        session.setTitle("新对话");
        session.setMessageCount(0);
        session.setDeleted(0);
        chatSessionMapper.insert(session);
        return session;
    }

    public List<ChatSessionEntity> list(Long userId, String keyword) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getUserId, userId)
                .eq(ChatSessionEntity::getDeleted, 0)
                .orderByDesc(ChatSessionEntity::getUpdatedAt);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(ChatSessionEntity::getTitle, keyword.trim());
        }
        return chatSessionMapper.selectList(wrapper);
    }

    public SessionDetail detail(Long userId, Long sessionId) {
        ChatSessionEntity session = owned(userId, sessionId);
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getCreatedAt));
        List<Long> messageIds = messages.stream().map(ChatMessageEntity::getId).collect(Collectors.toList());
        List<ChatCitationEntity> citations = messageIds.isEmpty()
                ? java.util.Collections.<ChatCitationEntity>emptyList()
                : chatCitationMapper.selectList(new LambdaQueryWrapper<ChatCitationEntity>()
                .in(ChatCitationEntity::getMessageId, messageIds)
                .orderByAsc(ChatCitationEntity::getId));
        return new SessionDetail(session, messages, citations);
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        ChatSessionEntity session = owned(userId, sessionId);
        session.setDeleted(1);
        chatSessionMapper.updateById(session);
    }

    @Transactional
    public void batchDelete(Long userId, List<Long> ids) {
        for (Long id : ids) {
            delete(userId, id);
        }
    }

    @Transactional
    public ChatSessionEntity patchScope(Long userId, Long sessionId, String scope) {
        ChatSessionEntity session = owned(userId, sessionId);
        session.setScope(scope);
        chatSessionMapper.updateById(session);
        return session;
    }

    @Transactional
    public void clear(Long userId, Long sessionId) {
        owned(userId, sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId));
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        session.setMessageCount(0);
        session.setLastQuestion(null);
        chatSessionMapper.updateById(session);
    }

    @Transactional
    public String share(Long userId, Long sessionId) {
        ChatSessionEntity session = owned(userId, sessionId);
        session.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        chatSessionMapper.updateById(session);
        return "https://example.local/share/" + session.getShareToken();
    }

    public ChatSessionEntity owned(Long userId, Long sessionId) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId()) || session.getDeleted() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会话不存在");
        }
        return session;
    }

    @Data
    @AllArgsConstructor
    public static class SessionDetail {
        private ChatSessionEntity session;
        private List<ChatMessageEntity> messages;
        private List<ChatCitationEntity> citations;
    }
}
