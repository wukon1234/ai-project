package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.client.EmbeddingClient;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.mapper.KbChunkMapper;
import com.zhishiyun.kb.service.MilvusChunkService.VectorHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 检索服务：默认走 Embedding + Milvus ANN；失败时回落关键词打分；hybrid 开启后 RRF 融合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private static final int RRF_K = 60;

    private final KbChunkMapper kbChunkMapper;
    private final EmbeddingClient embeddingClient;
    private final MilvusChunkService milvusChunkService;

    @Value("${kb.rag.hybrid-enabled:false}")
    private boolean hybridEnabled;

    /** 多库检索：优先向量 ANN；hybrid 开启时与关键词 RRF 融合。 */
    public List<SearchHit> search(String question, Set<String> libraryScopes, int topK) {
        if (libraryScopes == null || libraryScopes.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<SearchHit> vectorHits = vectorSearch(question, buildKbExpr(libraryScopes), topK);
        if (hybridEnabled) {
            List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                    .in(KbChunkEntity::getLibraryCode, libraryScopes));
            return hybridRank(vectorHits, keywordRank(question, chunks, Math.max(topK, 20)), topK);
        }
        if (!vectorHits.isEmpty()) {
            return vectorHits;
        }
        List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                .in(KbChunkEntity::getLibraryCode, libraryScopes));
        log.info("vector search empty/fallback keyword, scopes={}, qLen={}",
                libraryScopes.size(), question == null ? 0 : question.length());
        return rank(question, chunks, topK, null);
    }

    /** 单文档内检索（同文档问答）。 */
    public List<SearchHit> searchInDoc(String question, Long docId, int topK) {
        List<SearchHit> vectorHits = vectorSearch(question, buildDocExpr(docId), topK);
        if (hybridEnabled) {
            List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                    .eq(KbChunkEntity::getDocId, docId));
            return hybridRank(vectorHits, keywordRank(question, chunks, Math.max(topK, 20)), topK);
        }
        if (!vectorHits.isEmpty()) {
            return vectorHits;
        }
        List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                .eq(KbChunkEntity::getDocId, docId));
        return rank(question, chunks, topK, null);
    }

    /** 同文档相关片段：以当前页文本为种子，可排除本页。 */
    public List<SearchHit> relatedInDoc(Long docId, Integer pageNo, String seedText, int topK) {
        List<SearchHit> vectorHits = vectorSearch(seedText, buildDocExpr(docId), Math.max(topK * 3, 20));
        if (!vectorHits.isEmpty()) {
            return vectorHits.stream()
                    .filter(h -> pageNo == null || h.getChunk().getPageNo() == null
                            || !pageNo.equals(h.getChunk().getPageNo()))
                    .limit(topK)
                    .collect(Collectors.toList());
        }
        List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                .eq(KbChunkEntity::getDocId, docId));
        return rank(seedText, chunks, topK, pageNo);
    }

    /**
     * 混合检索：向量排行 + 关键词排行 → RRF 融合。
     */
    List<SearchHit> hybridRank(List<SearchHit> vectorList, List<SearchHit> keywordList, int topK) {
        Map<Long, Double> rrf = new HashMap<Long, Double>();
        Map<Long, KbChunkEntity> chunkMap = new HashMap<Long, KbChunkEntity>();
        for (int i = 0; i < vectorList.size(); i++) {
            SearchHit hit = vectorList.get(i);
            Long id = hit.getChunk().getId();
            chunkMap.put(id, hit.getChunk());
            rrf.put(id, rrf.getOrDefault(id, 0D) + 1D / (RRF_K + i + 1));
        }
        for (int i = 0; i < keywordList.size(); i++) {
            SearchHit hit = keywordList.get(i);
            Long id = hit.getChunk().getId();
            chunkMap.put(id, hit.getChunk());
            rrf.put(id, rrf.getOrDefault(id, 0D) + 1D / (RRF_K + i + 1));
        }
        List<SearchHit> fused = rrf.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new SearchHit(chunkMap.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
        log.info("hybrid RRF enabled, vector={}, keyword={}, fused={}",
                vectorList.size(), keywordList.size(), fused.size());
        return fused;
    }

    /** 兼容旧单测：对内存 chunk 列表做关键词/窗口打分（无 Milvus）。 */
    List<SearchHit> hybridRank(String question, List<KbChunkEntity> chunks, int topK) {
        List<SearchHit> vectorList = rank(question, chunks, Math.max(topK, 20), null);
        List<SearchHit> keywordList = keywordRank(question, chunks, Math.max(topK, 20));
        return hybridRank(vectorList, keywordList, topK);
    }

    private List<SearchHit> vectorSearch(String question, String expr, int topK) {
        if (!StringUtils.hasText(question) || embeddingClient == null || milvusChunkService == null) {
            return java.util.Collections.emptyList();
        }
        try {
            List<float[]> vectors = embeddingClient.embed(java.util.Collections.singletonList(question));
            if (vectors == null || vectors.isEmpty() || vectors.get(0) == null) {
                return java.util.Collections.emptyList();
            }
            List<VectorHit> hits = milvusChunkService.search(vectors.get(0), expr, topK);
            if (hits.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            List<Long> ids = new ArrayList<Long>(hits.size());
            Map<Long, Float> scoreMap = new LinkedHashMap<Long, Float>();
            for (VectorHit h : hits) {
                ids.add(h.getId());
                scoreMap.put(h.getId(), h.getScore());
            }
            List<KbChunkEntity> chunks = kbChunkMapper.selectBatchIds(ids);
            Map<Long, KbChunkEntity> byId = new HashMap<Long, KbChunkEntity>();
            for (KbChunkEntity c : chunks) {
                byId.put(c.getId(), c);
            }
            List<SearchHit> result = new ArrayList<SearchHit>();
            for (Long id : ids) {
                KbChunkEntity c = byId.get(id);
                if (c != null) {
                    result.add(new SearchHit(c, scoreMap.get(id)));
                }
            }
            log.info("milvus ANN hit, expr={}, topK={}, returned={}", expr, topK, result.size());
            return result;
        } catch (Exception e) {
            log.warn("milvus ANN failed, will fallback keyword: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private static String buildKbExpr(Set<String> libraryScopes) {
        StringBuilder sb = new StringBuilder("kb_id in [");
        boolean first = true;
        for (String code : libraryScopes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append('"').append(code.replace("\"", "")).append('"');
        }
        sb.append(']');
        return first ? null : sb.toString();
    }

    private static String buildDocExpr(Long docId) {
        if (docId == null) {
            return null;
        }
        return "doc_id == \"" + docId + "\"";
    }

    private List<SearchHit> keywordRank(String question, List<KbChunkEntity> chunks, int topK) {
        if (!StringUtils.hasText(question)) {
            return java.util.Collections.emptyList();
        }
        String q = question.toLowerCase().trim();
        List<SearchHit> hits = new ArrayList<SearchHit>();
        for (KbChunkEntity c : chunks) {
            if (c.getContent() == null) {
                continue;
            }
            String content = c.getContent().toLowerCase();
            int score = 0;
            if (content.contains(q)) {
                score += 10;
            }
            for (String token : q.split("[\\s，。？、；：！,.!?;:]+")) {
                if (token.length() > 1 && content.contains(token)) {
                    score++;
                }
            }
            if (score > 0) {
                hits.add(new SearchHit(c, score));
            }
        }
        hits.sort(Comparator.comparing(SearchHit::getScore).reversed());
        if (hits.size() > topK) {
            return hits.subList(0, topK);
        }
        return hits;
    }

    private List<SearchHit> rank(String question, List<KbChunkEntity> chunks, int topK, Integer excludePageNo) {
        return chunks.stream()
                .filter(c -> excludePageNo == null || c.getPageNo() == null || !excludePageNo.equals(c.getPageNo()))
                .map(c -> new SearchHit(c, score(question, c.getContent())))
                .filter(h -> h.getScore() > 0)
                .sorted(Comparator.comparing(SearchHit::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    double score(String question, String content) {
        if (question == null || content == null) {
            return 0;
        }
        String q = question.toLowerCase().trim();
        String lowerContent = content.toLowerCase();
        if (q.isEmpty() || lowerContent.isEmpty()) {
            return 0;
        }
        if (lowerContent.contains(q)) {
            return 1.0;
        }
        String[] tokens = q.split("[\\s，。？、；：！,.!?;:]+");
        int hit = 0;
        int total = 0;
        for (String token : tokens) {
            if (token.length() <= 1) {
                continue;
            }
            total++;
            if (lowerContent.contains(token)) {
                hit++;
            }
        }
        double tokenScore = total == 0 ? 0 : ((double) hit) / total;
        int windowHits = 0;
        int windows = 0;
        for (int i = 0; i + 1 < q.length(); i++) {
            String win = q.substring(i, i + 2);
            if (win.trim().isEmpty()) {
                continue;
            }
            windows++;
            if (lowerContent.contains(win)) {
                windowHits++;
            }
        }
        double windowScore = windows == 0 ? 0 : ((double) windowHits) / windows;
        return Math.max(tokenScore, windowScore);
    }

    /** 供单测切换 hybrid 开关。 */
    void setHybridEnabled(boolean hybridEnabled) {
        this.hybridEnabled = hybridEnabled;
    }

    @Data
    @AllArgsConstructor
    public static class SearchHit {
        private KbChunkEntity chunk;
        private double score;
    }
}
