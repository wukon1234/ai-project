package com.zhishiyun.kb.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.document.dto.DocumentMetaResponse;
import com.zhishiyun.kb.infra.mysql.entity.AuditLogEntity;
import com.zhishiyun.kb.infra.mysql.entity.FavDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbAclEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.AuditLogMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbAclMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import com.zhishiyun.kb.ingest.LocalStorageService;
import java.io.File;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CACHE_PREFIX = "doc:meta:";

    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final KbAclMapper kbAclMapper;
    private final FavDocumentMapper favDocumentMapper;
    private final UsageEventMapper usageEventMapper;
    private final AuditLogMapper auditLogMapper;
    private final LocalStorageService localStorageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DocumentMetaResponse meta(Long userId, Long docId) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        String key = CACHE_PREFIX + docId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isEmpty()) {
                DocumentMetaResponse resp = objectMapper.readValue(cached, DocumentMetaResponse.class);
                resp.setFavorited(isFavorited(userId, docId));
                return resp;
            }
        } catch (Exception ignored) {
        }
        KbLibraryEntity library = kbLibraryMapper.selectById(doc.getLibraryId());
        DocumentMetaResponse response = DocumentMetaResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .knowledgeBase(library == null ? doc.getLibraryCode() : library.getName())
                .knowledgeBaseId(doc.getLibraryCode())
                .fileType(doc.getFileType())
                .pages(doc.getPages())
                .summary(doc.getSummary())
                .updatedAt(doc.getUpdatedAt() == null ? null : doc.getUpdatedAt().format(DATE_TIME_FORMATTER))
                .views(doc.getViewCount())
                .favorited(isFavorited(userId, docId))
                .category(doc.getCategory())
                .build();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), java.time.Duration.ofMinutes(30));
        } catch (JsonProcessingException ignored) {
        }
        return response;
    }

    public File file(Long userId, Long docId, boolean download) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        writeAudit(userId, download ? "DOWNLOAD_DOC" : "PREVIEW_DOC", "document", String.valueOf(docId));
        return localStorageService.getFile(doc.getStorageKey());
    }

    @Transactional
    public Integer addView(Long userId, Long docId, Integer pageNo) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        int next = (doc.getViewCount() == null ? 0 : doc.getViewCount()) + 1;
        doc.setViewCount(next);
        kbDocumentMapper.updateById(doc);
        redisTemplate.delete(CACHE_PREFIX + docId);
        UsageEventEntity usage = new UsageEventEntity();
        usage.setUserId(userId);
        usage.setEventType("VIEW_DOC");
        usage.setLibraryCode(doc.getLibraryCode());
        usage.setRefId(String.valueOf(docId));
        usage.setExtraJson(pageNo == null ? null : "{\"pageNo\":" + pageNo + "}");
        usageEventMapper.insert(usage);
        return next;
    }

    public String share(Long userId, Long docId) {
        getPermittedDocument(userId, docId);
        return "https://example.local/documents/" + docId + "/share-token";
    }

    public KbDocumentEntity getPermittedDocument(Long userId, Long docId) {
        KbDocumentEntity doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档不存在");
        }
        Long count = kbAclMapper.selectCount(new LambdaQueryWrapper<KbAclEntity>()
                .eq(KbAclEntity::getUserId, userId)
                .eq(KbAclEntity::getLibraryCode, doc.getLibraryCode()));
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.FORBIDDEN_LIBRARY);
        }
        return doc;
    }

    private Boolean isFavorited(Long userId, Long docId) {
        Long count = favDocumentMapper.selectCount(new LambdaQueryWrapper<FavDocumentEntity>()
                .eq(FavDocumentEntity::getUserId, userId)
                .eq(FavDocumentEntity::getDocId, docId));
        return count != null && count > 0;
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
