package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.dto.DocumentMetaResponse;
import com.zhishiyun.kb.entity.FavDocumentEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.entity.UsageEventEntity;
import com.zhishiyun.kb.mapper.FavDocumentMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.UsageEventMapper;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 原文阅读：元数据、下载、ACL 校验、浏览埋点与分享 token。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 进程内分享 token 存储（单实例有效） */
    private static final Map<String, ShareEntry> LOCAL_SHARE_STORE = new java.util.concurrent.ConcurrentHashMap<String, ShareEntry>();

    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final LibraryAccessService libraryAccessService;
    private final FavDocumentMapper favDocumentMapper;
    private final UsageEventMapper usageEventMapper;
    private final AuditService auditService;
    private final LocalStorageService localStorageService;
    private final ObjectMapper objectMapper;

    /** 文档分享链接过期小时数 */
    @Value("${kb.share.doc-expire-hours:72}")
    private int shareExpireHours;
    /** 禁止外链的敏感知识库，逗号分隔 */
    @Value("${kb.share.blocked-libraries:}")
    private String shareBlockedLibraries;
    @Value("${kb.share.base-url:http://localhost:5173}")
    private String shareBaseUrl;

    /** 文档元数据，并记录浏览。 */
    public DocumentMetaResponse meta(Long userId, Long docId) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
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
        return response;
    }

    /** 返回原文文件（预览或下载），需通过 ACL。 */
    public File file(Long userId, Long docId, boolean download) {
        KbDocumentEntity doc = getPermittedDocument(userId, docId);
        String storageKey = doc.getStorageKey();
        if (!StringUtils.hasText(storageKey)) {
            log.warn("document file missing storageKey, userId={}, docId={}, download={}", userId, docId, download);
            throw new BizException(ErrorCode.BIZ_ERROR, "暂无原文文件");
        }
        File file = localStorageService.getFile(storageKey);
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("document file not found, userId={}, docId={}, storageKey={}, download={}",
                    userId, docId, storageKey, download);
            throw new BizException(ErrorCode.BIZ_ERROR, "暂无原文文件");
        }
        writeAudit(userId, download ? "DOWNLOAD_DOC" : "PREVIEW_DOC", "document", String.valueOf(docId),
                (download ? "下载文档：" : "预览文档：") + doc.getTitle());
        log.info("document file access, userId={}, docId={}, download={}", userId, docId, download);
        return file;
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
        LOCAL_SHARE_STORE.put(token, new ShareEntry(payload, System.currentTimeMillis() + shareExpireHours * 3600_000L));
        writeAudit(userId, "SHARE_DOC", "document", String.valueOf(docId), "分享文档：" + doc.getTitle());
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("shareToken", token);
        String base = shareBaseUrl == null ? "http://localhost:5173" : shareBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        data.put("shareUrl", base + "/?view=share-document&token=" + token);
        data.put("expireHours", shareExpireHours);
        log.info("document share created, userId={}, docId={}, expireHours={}", userId, docId, shareExpireHours);
        return data;
    }

    /** 只读解析文档分享 token。 */
    public Long resolveSharedDocId(String token) {
        ShareEntry entry = LOCAL_SHARE_STORE.get(token);
        if (entry == null) {
            log.warn("doc share token invalid or expired");
            throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效或已过期");
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            LOCAL_SHARE_STORE.remove(token);
            log.warn("doc share token invalid or expired");
            throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效或已过期");
        }
        String payload = entry.payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Object docId = map.get("docId");
            return Long.valueOf(String.valueOf(docId));
        } catch (Exception e) {
            log.warn("doc share token parse failed: {}", e.getMessage());
            throw new BizException(ErrorCode.PARAM_INVALID, "分享链接无效");
        }
    }

    /** 校验文档存在且用户具备库权限，否则抛 FORBIDDEN。 */
    public KbDocumentEntity getPermittedDocument(Long userId, Long docId) {
        KbDocumentEntity doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            log.warn("document not found, userId={}, docId={}", userId, docId);
            throw new BizException(ErrorCode.PARAM_INVALID, "文档不存在");
        }
        if (!libraryAccessService.canRead(userId, doc.getLibraryCode())) {
            log.warn("document acl denied, userId={}, docId={}, library={}", userId, docId, doc.getLibraryCode());
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

    private void writeAudit(Long userId, String action, String targetType, String targetId, String detail) {
        auditService.write(userId, action, targetType, targetId, detail);
    }

    /** 进程内分享条目：payload + 过期时间戳。 */
    private static class ShareEntry {
        private final String payload;
        private final long expireAt;

        ShareEntry(String payload, long expireAt) {
            this.payload = payload;
            this.expireAt = expireAt;
        }
    }
}
