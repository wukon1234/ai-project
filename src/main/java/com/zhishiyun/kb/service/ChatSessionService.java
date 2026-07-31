package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.ChatCitationEntity;
import com.zhishiyun.kb.entity.ChatMessageEntity;
import com.zhishiyun.kb.entity.ChatSessionEntity;
import com.zhishiyun.kb.mapper.ChatCitationMapper;
import com.zhishiyun.kb.mapper.ChatMessageMapper;
import com.zhishiyun.kb.mapper.ChatSessionMapper;
import com.zhishiyun.kb.service.ProfileService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 对话会话 CRUD：创建、列表、改 scope、批量删除。 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatCitationMapper chatCitationMapper;
    private final ProfileService profileService;

    /** 创建会话；未传 scope 时用用户默认知识库。 */
    @Transactional
    public ChatSessionEntity create(Long userId, String scope) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUserId(userId);
        if (scope == null || scope.trim().isEmpty()) {
            List<String> defaults = java.util.Collections.emptyList();
            try {
                defaults = profileService.defaultScopes(userId);
            } catch (Exception ignored) {
                // 偏好读取失败时回退默认 scope，避免创建会话整体失败
            }
            session.setScope(defaults.isEmpty() ? "hr" : String.join(",", defaults));
        } else {
            session.setScope(scope);
        }
        session.setTitle("新对话");
        session.setMessageCount(0);
        session.setDeleted(0);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        chatSessionMapper.insert(session);
        return session;
    }

    /** 会话列表，支持标题/最近问题关键词。 */
    public List<ChatSessionEntity> list(Long userId, String keyword) {
        LambdaQueryWrapper<ChatSessionEntity> wrapper = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getUserId, userId)
                .eq(ChatSessionEntity::getDeleted, 0)
                .orderByDesc(ChatSessionEntity::getUpdatedAt);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(ChatSessionEntity::getTitle, keyword.trim())
                    .or().like(ChatSessionEntity::getLastQuestion, keyword.trim()));
        }
        return chatSessionMapper.selectList(wrapper);
    }

    /** 会话详情（含消息与引用）。 */
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

    /** 软删除单个会话。 */
    @Transactional
    public void delete(Long userId, Long sessionId) {
        ChatSessionEntity session = owned(userId, sessionId);
        session.setDeleted(1);
        chatSessionMapper.updateById(session);
    }

    /** 批量软删除会话。 */
    @Transactional
    public void batchDelete(Long userId, List<Long> ids) {
        for (Long id : ids) {
            delete(userId, id);
        }
    }

    /** 切换会话检索范围。 */
    @Transactional
    public ChatSessionEntity patchScope(Long userId, Long sessionId, String scope) {
        ChatSessionEntity session = owned(userId, sessionId);
        session.setScope(scope);
        chatSessionMapper.updateById(session);
        return session;
    }

    /** 清空会话内消息与引用，保留会话本身。 */
    @Transactional
    public void clear(Long userId, Long sessionId) {
        owned(userId, sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId));
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        session.setMessageCount(0);
        session.setLastQuestion(null);
        chatSessionMapper.updateById(session);
    }

    /** 生成会话分享 token。 */
    @Transactional
    public String share(Long userId, Long sessionId) {
        ChatSessionEntity session = owned(userId, sessionId);
        session.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        chatSessionMapper.updateById(session);
        return "https://example.local/share/" + session.getShareToken();
    }

    /** 校验会话归属当前用户。 */
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
