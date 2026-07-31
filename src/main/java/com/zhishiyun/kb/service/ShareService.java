package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.ChatMessageEntity;
import com.zhishiyun.kb.entity.ChatSessionEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.mapper.ChatMessageMapper;
import com.zhishiyun.kb.mapper.ChatSessionMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话 / 文档分享只读访问。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionService chatSessionService;
    private final DocumentService documentService;
    private final KbDocumentMapper kbDocumentMapper;
    private final AuditService auditService;

    @Value("${kb.share.base-url:http://localhost:5173}")
    private String shareBaseUrl;
    @Value("${kb.share.require-login:false}")
    private boolean requireLogin;

    /** 为会话生成分享 token，并写审计日志。 */
    @Transactional
    public Map<String, Object> shareSession(Long userId, Long sessionId) {
        ChatSessionEntity session = chatSessionService.owned(userId, sessionId);
        String token = UUID.randomUUID().toString().replace("-", "");
        session.setShareToken(token);
        chatSessionMapper.updateById(session);
        writeAudit(userId, "SHARE_SESSION", "session", String.valueOf(sessionId),
                "分享会话：" + (session.getTitle() == null ? sessionId : session.getTitle()));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("shareToken", token);
        data.put("shareUrl", trimSlash(shareBaseUrl) + "/?view=share-session&token=" + token);
        log.info("session share created, userId={}, sessionId={}", userId, sessionId);
        return data;
    }

    /** 按 token 读取分享会话（只读消息列表）。 */
    public Map<String, Object> readSessionByToken(String token) {
        ChatSessionEntity session = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getShareToken, token)
                .eq(ChatSessionEntity::getDeleted, 0)
                .last("limit 1"));
        if (session == null) {
            log.warn("shared session not found for token");
            throw new BizException(ErrorCode.PARAM_INVALID, "分享会话不存在");
        }
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, session.getId())
                .orderByAsc(ChatMessageEntity::getCreatedAt));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("title", session.getTitle());
        data.put("scope", session.getScope());
        data.put("messages", messages.stream().map(m -> {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("role", m.getRole());
            row.put("content", m.getContent());
            row.put("answerStatus", m.getAnswerStatus());
            return row;
        }).collect(Collectors.toList()));
        data.put("requireLogin", requireLogin);
        log.info("shared session read, sessionId={}, messageCount={}", session.getId(), messages.size());
        return data;
    }

    /** 按 token 读取分享文档元数据。 */
    public Map<String, Object> readDocumentByToken(String token) {
        Long docId = documentService.resolveSharedDocId(token);
        KbDocumentEntity doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            log.warn("shared document not found, docId={}", docId);
            throw new BizException(ErrorCode.PARAM_INVALID, "分享文档不存在");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", doc.getId());
        data.put("title", doc.getTitle());
        data.put("libraryCode", doc.getLibraryCode());
        data.put("pages", doc.getPages());
        data.put("summary", doc.getSummary());
        data.put("fileType", doc.getFileType());
        data.put("requireLogin", requireLogin);
        log.info("shared document read, docId={}", docId);
        return data;
    }

    /** 写入分享相关审计日志。 */
    private void writeAudit(Long userId, String action, String targetType, String targetId, String detail) {
        auditService.write(userId, action, targetType, targetId, detail);
    }

    private String trimSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "http://localhost:5173";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
