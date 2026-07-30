package com.zhishiyun.kb.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.infra.mysql.entity.KbAclEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbChunkEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbAclMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbChunkMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> HOT_SEEDS = Arrays.asList("报销流程", "年假规定", "产品A规格", "考勤制度");

    private final KbAclMapper kbAclMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final UsageEventMapper usageEventMapper;
    private final StringRedisTemplate redisTemplate;

    public Map<String, Object> search(Long userId, String q, String category, String sort, int page, int size) {
        List<String> scopes = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>().eq(KbAclEntity::getUserId, userId))
                .stream().map(KbAclEntity::getLibraryCode).distinct().collect(Collectors.toList());
        if (scopes.isEmpty()) {
            return pageResult(page, size, new ArrayList<Map<String, Object>>());
        }
        LambdaQueryWrapper<KbDocumentEntity> wrapper = new LambdaQueryWrapper<KbDocumentEntity>()
                .in(KbDocumentEntity::getLibraryCode, scopes);
        if (StringUtils.hasText(category) && !"all".equals(category)) {
            wrapper.eq(KbDocumentEntity::getLibraryCode, category);
        }
        List<KbDocumentEntity> docs = kbDocumentMapper.selectList(wrapper);
        List<SearchRow> rows = new ArrayList<SearchRow>();
        for (KbDocumentEntity doc : docs) {
            String body = (doc.getTitle() == null ? "" : doc.getTitle()) + " " + (doc.getSummary() == null ? "" : doc.getSummary());
            List<KbChunkEntity> chunkHits = new ArrayList<KbChunkEntity>();
            if (StringUtils.hasText(q)) {
                List<KbChunkEntity> chunks = kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunkEntity>()
                        .eq(KbChunkEntity::getDocId, doc.getId()));
                for (KbChunkEntity c : chunks) {
                    if (containsIgnoreCase(c.getContent(), q)) {
                        chunkHits.add(c);
                    }
                }
            }
            boolean titleHit = containsIgnoreCase(doc.getTitle(), q);
            boolean summaryHit = containsIgnoreCase(doc.getSummary(), q);
            boolean hit = !StringUtils.hasText(q) || titleHit || summaryHit || !chunkHits.isEmpty();
            if (!hit) {
                continue;
            }
            double score = 0;
            if (StringUtils.hasText(q)) {
                if (titleHit) score += 3.0;
                if (summaryHit) score += 1.5;
                score += Math.min(3.0, chunkHits.size() * 0.5);
            }
            rows.add(new SearchRow(doc, chunkHits.isEmpty() ? null : chunkHits.get(0), score));
        }
        rows.sort(buildComparator(sort, q));
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(rows.size(), from + size);
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        if (from < to) {
            for (SearchRow row : rows.subList(from, to)) {
                list.add(toResponse(row, q));
            }
        }
        if (StringUtils.hasText(q)) {
            redisTemplate.opsForZSet().incrementScore("search:hot", q, 1D);
            UsageEventEntity usage = new UsageEventEntity();
            usage.setUserId(userId);
            usage.setEventType("SEARCH");
            usage.setRefId(q);
            usageEventMapper.insert(usage);
        }
        Map<String, Object> data = pageResult(page, size, list);
        data.put("total", rows.size());
        return data;
    }

    public List<String> hotWords() {
        java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores("search:hot", 0, 9);
        if (tuples == null || tuples.isEmpty()) {
            return HOT_SEEDS;
        }
        List<String> result = tuples.stream().map(org.springframework.data.redis.core.ZSetOperations.TypedTuple::getValue)
                .filter(StringUtils::hasText).collect(Collectors.toList());
        return result.isEmpty() ? HOT_SEEDS : result;
    }

    private Comparator<SearchRow> buildComparator(String sort, String q) {
        if ("updated".equals(sort)) {
            return Comparator.comparing((SearchRow r) -> r.getDoc().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("views".equals(sort)) {
            return Comparator.comparing((SearchRow r) -> r.getDoc().getViewCount() == null ? 0 : r.getDoc().getViewCount()).reversed();
        }
        if (StringUtils.hasText(q)) {
            return Comparator.comparing(SearchRow::getScore).reversed()
                    .thenComparing(r -> r.getDoc().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing((SearchRow r) -> r.getDoc().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Map<String, Object> toResponse(SearchRow row, String q) {
        KbDocumentEntity doc = row.getDoc();
        KbLibraryEntity lib = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getCode, doc.getLibraryCode()).last("limit 1"));
        String text = row.getChunk() != null ? row.getChunk().getContent() : (doc.getSummary() == null ? "" : doc.getSummary());
        Highlight h = highlight(text, q);
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("fileType", doc.getFileType());
        m.put("category", doc.getCategory());
        m.put("knowledgeBase", lib == null ? doc.getLibraryCode() : lib.getName());
        m.put("pages", doc.getPages());
        m.put("updatedAt", doc.getUpdatedAt() == null ? null : doc.getUpdatedAt().format(FMT));
        m.put("views", doc.getViewCount());
        m.put("excerptBefore", h.getBefore());
        m.put("highlights", h.getHighlights());
        m.put("excerptAfter", h.getAfter());
        m.put("page", row.getChunk() == null ? 1 : row.getChunk().getPageNo());
        return m;
    }

    private Highlight highlight(String source, String q) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(q)) {
            return new Highlight(source == null ? "" : truncate(source, 40), new ArrayList<String>(), "");
        }
        String low = source.toLowerCase();
        String k = q.toLowerCase();
        int idx = low.indexOf(k);
        if (idx < 0) {
            return new Highlight(truncate(source, 40), new ArrayList<String>(), "");
        }
        int start = Math.max(0, idx - 20);
        int end = Math.min(source.length(), idx + q.length() + 20);
        String before = source.substring(start, idx);
        String hit = source.substring(idx, idx + q.length());
        String after = source.substring(idx + q.length(), end);
        return new Highlight(before, java.util.Collections.singletonList(hit), after);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private boolean containsIgnoreCase(String text, String q) {
        if (!StringUtils.hasText(q)) return true;
        if (!StringUtils.hasText(text)) return false;
        return text.toLowerCase().contains(q.toLowerCase());
    }

    private Map<String, Object> pageResult(int page, int size, List<Map<String, Object>> list) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        data.put("total", list.size());
        return data;
    }

    @Data
    @AllArgsConstructor
    private static class SearchRow {
        private KbDocumentEntity doc;
        private KbChunkEntity chunk;
        private double score;
    }

    @Data
    @AllArgsConstructor
    private static class Highlight {
        private String before;
        private List<String> highlights;
        private String after;
    }
}
