package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.client.LlmClient;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.entity.UsageEventEntity;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.UsageEventMapper;
import com.zhishiyun.kb.service.VectorSearchService.SearchHit;
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

/** 同文档流式问答：仅在当前文档分块内检索并 SSE 推送。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAskStreamService {

    private final DocumentService documentService;
    private final VectorSearchService vectorSearchService;
    private final KbLibraryMapper kbLibraryMapper;
    private final UsageEventMapper usageEventMapper;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${kb.rag.score-threshold:0.45}")
    private double scoreThreshold;
    @Value("${kb.rag.context-n:6}")
    private int contextN;

    /** 同文档流式问答入口。 */
    public SseEmitter askStream(Long userId, Long docId, String question) {
        SseEmitter emitter = new SseEmitter(0L);
        process(emitter, userId, docId, question);
        return emitter;
    }

    /** 校验文档权限后仅在该 doc 内检索，推送 citation/delta/done。 */
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

            String answer = buildAnswer(question, selected);
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
            if (e instanceof BizException) {
                BizException biz = (BizException) e;
                log.warn("document ask stream biz error, doc={}, code={}, msg={}", docId, biz.getCode(), biz.getMessage());
                try {
                    send(emitter, "error", mapOf("code", biz.getCode(), "message", biz.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.complete();
                return;
            }
            log.error("document ask stream failed, doc={}", docId, e);
            try {
                String msg = e.getMessage() == null || e.getMessage().trim().isEmpty()
                        ? "暂无数据"
                        : e.getMessage();
                send(emitter, "error", mapOf("code", 50001, "message", msg));
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

    /** 基于本文档检索片段调用 LLM 生成回答。 */
    private String buildAnswer(String question, List<SearchHit> hits) {
        StringBuilder context = new StringBuilder();
        int idx = 1;
        for (SearchHit hit : hits) {
            context.append("[").append(idx++).append("] ")
                    .append(contextSnippet(hit.getChunk().getContent()))
                    .append("\n");
        }
        String system = "你是企业文档助手。请仅依据给定文档片段回答，必要时用 [n] 标注来源；"
                + "不要编造片段外信息；不足时请明确说明。";
        String user = "用户问题：\n" + (question == null ? "" : question)
                + "\n\n文档片段：\n" + context;
        return llmClient.chat(system, user);
    }

    private String contextSnippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() > 800 ? t.substring(0, 800) : t;
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
