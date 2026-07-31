package com.zhishiyun.kb.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.infra.mysql.entity.KbChunkEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import com.zhishiyun.kb.rag.VectorSearchService;
import com.zhishiyun.kb.rag.VectorSearchService.SearchHit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAskStreamService {

    private final DocumentService documentService;
    private final VectorSearchService vectorSearchService;
    private final KbLibraryMapper kbLibraryMapper;
    private final UsageEventMapper usageEventMapper;
    private final ObjectMapper objectMapper;

    @Value("${kb.rag.score-threshold:0.45}")
    private double scoreThreshold;
    @Value("${kb.rag.context-n:6}")
    private int contextN;

    public SseEmitter askStream(Long userId, Long docId, String question) {
        SseEmitter emitter = new SseEmitter(0L);
        process(emitter, userId, docId, question);
        return emitter;
    }

    private void process(SseEmitter emitter, Long userId, Long docId, String question) {
        long start = System.currentTimeMillis();
        try {
            KbDocumentEntity doc = documentService.getPermittedDocument(userId, docId);
            String messageId = "doc-ask-" + docId + "-" + start;
            send(emitter, "meta", mapOf("messageId", messageId, "status", "SEARCHING", "docId", String.valueOf(docId)));

            List<SearchHit> hits = vectorSearchService.searchInDoc(question, docId, 20);
            List<SearchHit> selected = hits.stream()
                    .filter(h -> h.getScore() >= scoreThreshold)
                    .filter(h -> docId.equals(h.getChunk().getDocId()))
                    .limit(contextN)
                    .collect(Collectors.toList());

            if (selected.isEmpty()) {
                send(emitter, "done", mapOf(
                        "elapsedMs", System.currentTimeMillis() - start,
                        "status", "NO_ANSWER",
                        "disclaimer", "AI 可能出错，请以原文为准",
                        "suggestions", java.util.Arrays.asList("换个问法", "翻到相关页后再问", "查看相关片段")));
                writeUsage(userId, doc, question);
                emitter.complete();
                return;
            }

            KbLibraryEntity library = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                    .eq(KbLibraryEntity::getCode, doc.getLibraryCode()).last("limit 1"));
            int idx = 1;
            for (SearchHit hit : selected) {
                KbChunkEntity chunk = hit.getChunk();
                send(emitter, "citation", mapOf(
                        "index", idx++,
                        "docId", String.valueOf(docId),
                        "title", doc.getTitle(),
                        "page", chunk.getPageNo(),
                        "knowledgeBase", library == null ? doc.getLibraryCode() : library.getName(),
                        "knowledgeBaseId", doc.getLibraryCode(),
                        "excerpt", excerpt(chunk.getContent())));
            }

            String answer = buildAnswer(selected);
            for (String part : splitForStreaming(answer)) {
                send(emitter, "delta", mapOf("content", part));
            }
            send(emitter, "done", mapOf(
                    "elapsedMs", System.currentTimeMillis() - start,
                    "status", "OK",
                    "disclaimer", "AI 可能出错，请以原文为准"));
            writeUsage(userId, doc, question);
            emitter.complete();
        } catch (Exception e) {
            log.error("document ask stream failed, doc={}", docId, e);
            try {
                send(emitter, "error", mapOf("code", 50001, "message", e.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    private void writeUsage(Long userId, KbDocumentEntity doc, String question) {
        UsageEventEntity usage = new UsageEventEntity();
        usage.setUserId(userId);
        usage.setEventType("ASK");
        usage.setLibraryCode(doc.getLibraryCode());
        usage.setRefId(String.valueOf(doc.getId()));
        usage.setExtraJson("{\"scope\":\"document\",\"question\":\"" +
                (question == null ? "" : question.replace("\"", "'")) + "\"}");
        usageEventMapper.insert(usage);
    }

    private String buildAnswer(List<SearchHit> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("基于本文档相关片段：\n");
        int idx = 1;
        for (SearchHit hit : hits) {
            builder.append("[").append(idx++).append("] ")
                    .append(excerpt(hit.getChunk().getContent()))
                    .append("\n");
        }
        return builder.toString();
    }

    private List<String> splitForStreaming(String text) {
        List<String> list = new ArrayList<String>();
        int step = 20;
        for (int i = 0; i < text.length(); i += step) {
            list.add(text.substring(i, Math.min(text.length(), i + step)));
        }
        return list;
    }

    private String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
