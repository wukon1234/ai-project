package com.zhishiyun.kb.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原文阅读：元数据、下载、ACL 校验、浏览埋点与分享 token。 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CACHE_PREFIX = "doc:meta:";
    private static final String DOC_SHARE_PREFIX = "share:doc:";
    /** Redis 不可用时的进程内分享 token 兜底 */
    private static final Map<String, String> LOCAL_SHARE_STORE = new java.util.concurrent.ConcurrentHashMap<String, String>();

    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final KbAclMapper kbAclMapper;
    private final FavDocumentMapper favDocumentMapper;
    private final UsageEventMapper usageEventMapper;
    private final AuditLogMapper auditLogMapper;
    private final LocalStorageService localStorageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 文档分享链接过期小时数 */
    @Value("${kb.share.doc-expire-hours:72}")
    private int shareExpireHours;
    /** 禁止外链的敏感知识库，逗号分隔 */
    @Value("${kb.share.blocked-libraries:}")
    private String shareBlockedLibraries;
    @Value("${kb.share.base-url:http://localhost:8080}")
    private String shareBaseUrl;

    /** 文档元数据；带 Redis 短缓存，并记录浏览。 */
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
        } catch (Exception ignored) {
        }
        return response;
    }

    /** 返回原文文件（预览或下载），需通过 ACL。 */
    public File file(Long userId, Long docId, boolean download) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        writeAudit(userId, download ? "DOWNLOAD_DOC" : "PREVIEW_DOC", "document", String.valueOf(docId));
        return localStorageService.getFile(doc.getStorageKey());
    }

    /**
     * 浏览量 +1，并写入 OPEN_SOURCE / READ_COMPLETE 埋点。
     *
     * @param eventType OPEN_SOURCE（点击来源）或 READ_COMPLETE（完整阅读）
     * @param readMinutes 完整阅读时长（分钟），可选
     */
    @Transactional
    public Integer addView(Long userId, Long docId, Integer pageNo, String eventType, Double readMinutes) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        int next = (doc.getViewCount() == null ? 0 : doc.getViewCount()) + 1;
        doc.setViewCount(next);
        kbDocumentMapper.updateById(doc);
        try {
            redisTemplate.delete(CACHE_PREFIX + docId);
        } catch (Exception ignored) {
        }
        String type = "READ_COMPLETE".equalsIgnoreCase(eventType) ? "READ_COMPLETE" : "OPEN_SOURCE";
        StringBuilder extra = new StringBuilder("{");
        if (pageNo != null) {
            extra.append("\"pageNo\":").append(pageNo);
        }
        if (readMinutes != null) {
            if (pageNo != null) {
                extra.append(',');
            }
            extra.append("\"readMinutes\":").append(readMinutes);
        }
        extra.append('}');
        UsageEventEntity usage = new UsageEventEntity();
        usage.setUserId(userId);
        usage.setEventType(type);
        usage.setLibraryCode(doc.getLibraryCode());
        usage.setRefId(String.valueOf(docId));
        usage.setExtraJson("{}".equals(extra.toString()) ? null : extra.toString());
        usageEventMapper.insert(usage);
        return next;
    }

    /** 生成文档分享短链；敏感库可按配置禁止外链。 */
    public Map<String, Object> createShare(Long userId, Long docId) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        String blocked = shareBlockedLibraries;
        if (blocked != null) {
            for (String code : blocked.split(",")) {
                if (code.trim().equalsIgnoreCase(doc.getLibraryCode())) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "该知识库禁止外链分享");
                }
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        String payload = "{\"docId\":" + docId + ",\"userId\":" + userId + "}";
        try {
            redisTemplate.opsForValue().set(DOC_SHARE_PREFIX + token, payload, java.time.Duration.ofHours(shareExpireHours));
        } catch (Exception ex) {
            LOCAL_SHARE_STORE.put(token, payload);
        }
        writeAudit(userId, "SHARE_DOC", "document", String.valueOf(docId));
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("shareToken", token);
        data.put("shareUrl", shareBaseUrl + "/api/v1/share/documents/" + token);
        data.put("expireHours", shareExpireHours);
        return data;
    }

    /** 只读解析文档分享 token。 */
    public Long resolveSharedDocId(String token) {
        String payload = null;
        try {
            payload = redisTemplate.opsForValue().get(DOC_SHARE_PREFIX + token);
        } catch (Exception ignored) {
        }
        if (payload == null || payload.isEmpty()) {
            payload = LOCAL_SHARE_STORE.get(token);
        }
        if (payload == null || payload.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效或已过期");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Object docId = map.get("docId");
            return Long.valueOf(String.valueOf(docId));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效");
        }
    }

    /** 校验文档存在且用户具备库权限，否则抛 FORBIDDEN。 */
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
