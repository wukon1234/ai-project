package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.dto.PageSummaryResponse;
import com.zhishiyun.kb.dto.RelatedChunkResponse;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.mapper.KbChunkMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.service.VectorSearchService;
import com.zhishiyun.kb.service.VectorSearchService.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 阅读器 AI：页摘要缓存、同文档相关片段检索。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAiService {

    private static final String SUMMARY_CACHE_PREFIX = "page:summary:";
    /** 页摘要缓存有效期：24 小时 */
    private static final long SUMMARY_TTL_MS = 24 * 60 * 60 * 1000L;
    /** 进程内页摘要缓存：key -> {json, expireAt}，仅单实例有效 */
    private static final Map<String, CacheEntry> SUMMARY_CACHE = new ConcurrentHashMap<String, CacheEntry>();

    private final DocumentService documentService;
    private final KbChunkMapper kbChunkMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper;

    /** 页摘要：先读进程内缓存，未命中则由当页分块拼接生成。 */
    public PageSummaryResponse pageSummary(Long userId, Long docId, Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pageNo 无效");
        }
        KbDocumentEntity doc = documentService.getPermittedDocument(userId, docId);
        String cacheKey = SUMMARY_CACHE_PREFIX + docId + ":" + pageNo;
        CacheEntry entry = SUMMARY_CACHE.get(cacheKey);
        if (entry != null) {
            if (entry.expireAt < System.currentTimeMillis()) {
                SUMMARY_CACHE.remove(cacheKey);
            } else {
                try {
                    PageSummaryResponse resp = objectMapper.readValue(entry.json, PageSummaryResponse.class);
                    resp.setCached(true);
                    return resp;
                } catch (Exception ignored) {
                }
            }
        }

        KbLibraryEntity library = kbLibraryMapper.selectById(doc.getLibraryId());
        String knowledgeBase = library == null ? doc.getLibraryCode() : library.getName();
        String pageText = loadPageText(docId, pageNo);
        String summary;
        if (!StringUtils.hasText(pageText)) {
            summary = "本页暂无可摘要文本";
            log.info("page summary empty text, docId={}, pageNo={}", docId, pageNo);
        } else {
            summary = buildExtractiveSummary(pageText);
        }
        PageSummaryResponse response = PageSummaryResponse.builder()
                .pageNo(pageNo)
                .knowledgeBase(knowledgeBase)
                .summary(summary)
                .cached(false)
                .build();
        try {
            SUMMARY_CACHE.put(cacheKey, new CacheEntry(
                    objectMapper.writeValueAsString(response), System.currentTimeMillis() + SUMMARY_TTL_MS));
        } catch (Exception ignored) {
        }
        return response;
    }

    /** 同文档相关片段：以当前页内容为种子做检索。 */
    public List<RelatedChunkResponse> relatedChunks(Long userId, Long docId, Integer pageNo, Integer limit) {
        KbDocumentEntity doc = documentService.getPermittedDocument(userId, docId);
        int topK = (limit == null || limit < 1) ? 5 : Math.min(limit, 20);
        Integer seedPage = pageNo == null ? 1 : pageNo;
        String seedText = loadPageText(docId, seedPage);
        if (!StringUtils.hasText(seedText)) {
            seedText = doc.getTitle() == null ? "" : doc.getTitle();
        }
        List<SearchHit> hits = vectorSearchService.relatedInDoc(docId, seedPage, seedText, topK);
        if (hits.isEmpty()) {
            List<KbChunkEntity> others = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                    .eq(KbChunkEntity::getDocId, docId)
                    .ne(KbChunkEntity::getPageNo, seedPage)
                    .orderByAsc(KbChunkEntity::getPageNo)
                    .last("limit " + topK));
            List<RelatedChunkResponse> fallback = new ArrayList<RelatedChunkResponse>();
            for (KbChunkEntity c : others) {
                fallback.add(toRelated(doc, c));
            }
            log.info("related chunks fallback, docId={}, pageNo={}, count={}", docId, seedPage, fallback.size());
            return fallback;
        }
        log.info("related chunks hit, docId={}, pageNo={}, count={}", docId, seedPage, hits.size());
        return hits.stream().map(h -> toRelated(doc, h.getChunk())).collect(Collectors.toList());
    }

    /** 拼接指定页全部分块文本。 */
    public String loadPageText(Long docId, Integer pageNo) {
        List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                .eq(KbChunkEntity::getDocId, docId)
                .eq(KbChunkEntity::getPageNo, pageNo)
                .orderByAsc(KbChunkEntity::getChunkIndex));
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KbChunkEntity c : chunks) {
            if (c.getContent() != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(c.getContent());
            }
        }
        return sb.toString().trim();
    }

    /** 抽取式摘要：优先按句号截断，否则截到约 180 字。 */
    String buildExtractiveSummary(String pageText) {
        String normalized = pageText.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        int cut = normalized.indexOf('。', 80);
        if (cut > 0 && cut < 220) {
            return normalized.substring(0, cut + 1);
        }
        return normalized.substring(0, 180) + "…";
    }

    private RelatedChunkResponse toRelated(KbDocumentEntity doc, KbChunkEntity chunk) {
        String excerpt = chunk.getContent() == null ? "" : chunk.getContent().trim();
        if (excerpt.length() > 120) {
            excerpt = excerpt.substring(0, 120);
        }
        return RelatedChunkResponse.builder()
                .page(chunk.getPageNo())
                .title(doc.getTitle())
                .summary(excerpt)
                .excerpt(excerpt)
                .chunkId(chunk.getId())
                .build();
    }

    /** 进程内缓存条目：JSON 内容 + 过期时间戳。 */
    private static class CacheEntry {
        private final String json;
        private final long expireAt;

        CacheEntry(String json, long expireAt) {
            this.json = json;
            this.expireAt = expireAt;
        }
    }
}
