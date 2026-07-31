package com.zhishiyun.kb.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.document.dto.PageSummaryResponse;
import com.zhishiyun.kb.document.dto.RelatedChunkResponse;
import com.zhishiyun.kb.infra.mysql.entity.KbChunkEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbChunkMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.rag.VectorSearchService;
import com.zhishiyun.kb.rag.VectorSearchService.SearchHit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 阅读器 AI：页摘要缓存、同文档相关片段检索。 */
@Service
@RequiredArgsConstructor
public class DocumentAiService {

    private static final String SUMMARY_CACHE_PREFIX = "page:summary:";

    private final DocumentService documentService;
    private final KbChunkMapper kbChunkMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final VectorSearchService vectorSearchService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 页摘要：先读 Redis 缓存，未命中则由当页分块拼接生成。 */
    public PageSummaryResponse pageSummary(Long userId, Long docId, Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pageNo 无效");
        }
        KbDocumentEntity doc = documentService.getPermittedDocument(userId, docId);
        String cacheKey = SUMMARY_CACHE_PREFIX + docId + ":" + pageNo;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                PageSummaryResponse resp = objectMapper.readValue(cached, PageSummaryResponse.class);
                resp.setCached(true);
                return resp;
            }
        } catch (Exception ignored) {
        }

        KbLibraryEntity library = kbLibraryMapper.selectById(doc.getLibraryId());
        String knowledgeBase = library == null ? doc.getLibraryCode() : library.getName();
        String pageText = loadPageText(docId, pageNo);
        String summary;
        if (!StringUtils.hasText(pageText)) {
            summary = "本页暂无可摘要文本";
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
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofHours(24));
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
            return fallback;
        }
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
}
