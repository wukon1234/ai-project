package com.zhishiyun.kb.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.enums.AnswerStatus;
import com.zhishiyun.kb.common.enums.MessageRole;
import com.zhishiyun.kb.infra.mysql.entity.BizContactEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatCitationEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatSessionEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbChunkEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.mapper.BizContactMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatCitationMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatSessionMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.common.UsageEventService;
import com.zhishiyun.kb.config.TraceIdFilter;
import com.zhishiyun.kb.rag.VectorSearchService;
import com.zhishiyun.kb.rag.VectorSearchService.SearchHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 会话流式问答：ACL 范围内检索 → 引用 → SSE 推送答案。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatSessionService chatSessionService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatCitationMapper chatCitationMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final BizContactMapper bizContactMapper;
    private final LibraryAccessService libraryAccessService;
    private final VectorSearchService vectorSearchService;
    private final UsageEventService usageEventService;
    private final ObjectMapper objectMapper;

    @Value("${kb.rag.score-threshold:0.45}")
    private double scoreThreshold;
    @Value("${kb.rag.context-n:6}")
    private int contextN;

    /** 发起会话流式问答，立即返回 SSE emitter。 */
    public SseEmitter askStream(Long userId, Long sessionId, String question) {
        SseEmitter emitter = new SseEmitter(0L);
        process(emitter, userId, sessionId, question);
        return emitter;
    }

    /**
     * 问答主流程：落用户消息 → 检索 → 推送 citation/delta → 落助手消息与引用。
     * SSE 事件：meta / citation / delta / done / error。
     */
    @Transactional
    protected void process(SseEmitter emitter, Long userId, Long sessionId, String question) {
        long start = System.currentTimeMillis();
        try {
            ChatSessionEntity session = chatSessionService.owned(userId, sessionId);
            ChatMessageEntity userMsg = new ChatMessageEntity();
            userMsg.setSessionId(sessionId);
            userMsg.setRole(MessageRole.user.name());
            userMsg.setContent(question);
            chatMessageMapper.insert(userMsg);
            session.setLastQuestion(question);
            if ("新对话".equals(session.getTitle()) || session.getTitle() == null || session.getTitle().trim().isEmpty()) {
                session.setTitle(question.length() > 20 ? question.substring(0, 20) : question);
            }
            chatSessionMapper.updateById(session);

            // 埋点 ASK，供统计页聚合
            String scopeCode = session.getScope() == null ? "all" : session.getScope().split(",")[0];
            usageEventService.track(userId, "ASK", scopeCode, String.valueOf(userMsg.getId()),
                    "{\"question\":\"" + (question == null ? "" : question.replace("\"", "'")) + "\"}");

            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            send(emitter, "meta", mapOf(
                    "messageId", String.valueOf(userMsg.getId()),
                    "status", "SEARCHING",
                    "traceId", traceId == null ? "" : traceId));
            // scope ∩ ACL 后检索；低于阈值则走 NO_ANSWER
            Set<String> scopes = libraryAccessService.resolveScopes(userId, session.getScope());
            List<SearchHit> hits = vectorSearchService.search(question, scopes, 20);

            List<SearchHit> selected = hits.stream()
                    .filter(h -> h.getScore() >= scoreThreshold)
                    .limit(contextN)
                    .collect(Collectors.toList());
            if (selected.isEmpty()) {
                noAnswer(emitter, userId, sessionId, start);
                return;
            }
            List<ChatCitationEntity> citations = new ArrayList<ChatCitationEntity>();
            int idx = 1;
            for (SearchHit hit : selected) {
                KbChunkEntity chunk = hit.getChunk();
                KbDocumentEntity doc = kbDocumentMapper.selectById(chunk.getDocId());
                KbLibraryEntity library = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                        .eq(KbLibraryEntity::getCode, chunk.getLibraryCode()).last("limit 1"));
                send(emitter, "citation", mapOf(
                        "index", idx,
                        "docId", String.valueOf(chunk.getDocId()),
                        "title", doc == null ? "未知文档" : doc.getTitle(),
                        "page", chunk.getPageNo(),
                        "knowledgeBase", library == null ? chunk.getLibraryCode() : library.getName(),
                        "knowledgeBaseId", chunk.getLibraryCode(),
                        "excerpt", excerpt(chunk.getContent())));
                ChatCitationEntity cite = new ChatCitationEntity();
                cite.setCiteIndex(idx++);
                cite.setDocId(chunk.getDocId());
                cite.setTitle(doc == null ? "未知文档" : doc.getTitle());
                cite.setPageNo(chunk.getPageNo());
                cite.setLibraryName(library == null ? chunk.getLibraryCode() : library.getName());
                cite.setLibraryCode(chunk.getLibraryCode());
                cite.setExcerpt(excerpt(chunk.getContent()));
                citations.add(cite);
            }
            String answer = buildAnswer(selected);
            for (String part : splitForStreaming(answer)) {
                send(emitter, "delta", mapOf("content", part));
            }
            ChatMessageEntity aiMsg = new ChatMessageEntity();
            aiMsg.setSessionId(sessionId);
            aiMsg.setRole(MessageRole.assistant.name());
            aiMsg.setAnswerStatus(AnswerStatus.OK.name());
            aiMsg.setContent(answer);
            aiMsg.setElapsedMs((int) (System.currentTimeMillis() - start));
            chatMessageMapper.insert(aiMsg);
            for (ChatCitationEntity cite : citations) {
                cite.setMessageId(aiMsg.getId());
                chatCitationMapper.insert(cite);
            }
            send(emitter, "done", mapOf(
                    "messageId", String.valueOf(aiMsg.getId()),
                    "elapsedMs", System.currentTimeMillis() - start,
                    "status", "OK",
                    "disclaimer", "AI 可能出错，请以原文为准"));
            emitter.complete();
        } catch (Exception e) {
            log.error("ask stream failed", e);
            try {
                send(emitter, "error", mapOf("code", 50001, "message", e.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    /** 无命中：返回引导建议与业务联系人（默认 HR）。 */
    private void noAnswer(SseEmitter emitter, Long userId, Long sessionId, long start) throws IOException {
        BizContactEntity contact = bizContactMapper.selectOne(new LambdaQueryWrapper<BizContactEntity>()
                .eq(BizContactEntity::getLibraryCode, "hr").last("limit 1"));
        ChatMessageEntity aiMsg = new ChatMessageEntity();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole(MessageRole.assistant.name());
        aiMsg.setAnswerStatus(AnswerStatus.NO_ANSWER.name());
        aiMsg.setContent("抱歉，我在您有权访问的知识库中未找到相关信息。");
        aiMsg.setElapsedMs((int) (System.currentTimeMillis() - start));
        chatMessageMapper.insert(aiMsg);
        send(emitter, "done", mapOf(
                "messageId", String.valueOf(aiMsg.getId()),
                "elapsedMs", System.currentTimeMillis() - start,
                "status", "NO_ANSWER",
                "disclaimer", "AI 可能出错，请以原文为准",
                "suggestions", java.util.Arrays.asList("换个问法", "切换到全部知识库", "联系HR"),
                "contact", mapOf(
                        "name", contact == null ? "HR 联系人" : contact.getName(),
                        "title", contact == null ? "人力资源BP" : contact.getTitle(),
                        "wecom", contact == null ? "" : contact.getWecom(),
                        "extNo", contact == null ? "" : contact.getExtNo())));
        emitter.complete();
    }

    /** 基于检索片段拼装回答（后续可替换为 LLM 生成）。 */
    private String buildAnswer(List<SearchHit> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("根据检索到的资料：\n");
        int idx = 1;
        for (SearchHit hit : hits) {
            builder.append("[").append(idx++).append("] ")
                    .append(excerpt(hit.getChunk().getContent()))
                    .append("\n");
        }
        builder.append("\n以上为基于知识库片段的回答。");
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
