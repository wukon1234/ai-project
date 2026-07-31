package com.zhishiyun.kb.share;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.chat.ChatSessionService;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.document.DocumentService;
import com.zhishiyun.kb.infra.mysql.entity.AuditLogEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatSessionEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.mapper.AuditLogMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatSessionMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbDocumentMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话 / 文档分享只读访问。 */
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionService chatSessionService;
    private final DocumentService documentService;
    private final KbDocumentMapper kbDocumentMapper;
    private final AuditLogMapper auditLogMapper;

    @Value("${kb.share.base-url:http://localhost:8080}")
    private String shareBaseUrl;
    @Value("${kb.share.require-login:false}")
    private boolean requireLogin;

    @Transactional
    public Map<String, Object> shareSession(Long userId, Long sessionId) {
        ChatSessionEntity session = chatSessionService.owned(userId, sessionId);
        String token = UUID.randomUUID().toString().replace("-", "");
        session.setShareToken(token);
        chatSessionMapper.updateById(session);
        writeAudit(userId, "SHARE_SESSION", "session", String.valueOf(sessionId));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("shareToken", token);
        data.put("shareUrl", shareBaseUrl + "/api/v1/share/sessions/" + token);
        return data;
    }

    public Map<String, Object> readSessionByToken(String token) {
        ChatSessionEntity session = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getShareToken, token)
                .eq(ChatSessionEntity::getDeleted, 0)
                .last("limit 1"));
        if (session == null) {
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
        return data;
    }

    public Map<String, Object> readDocumentByToken(String token) {
        Long docId = documentService.resolveSharedDocId(token);
        KbDocumentEntity doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
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
        return data;
    }

    private void writeAudit(Long userId, String action, String targetType, String targetId) {
        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(action);
        auditLogMapper.insert(log);
    }
}
