package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.enums.AnswerStatus;
import com.zhishiyun.kb.common.enums.MessageRole;
import com.zhishiyun.kb.entity.BizContactEntity;
import com.zhishiyun.kb.entity.ChatCitationEntity;
import com.zhishiyun.kb.entity.ChatMessageEntity;
import com.zhishiyun.kb.entity.ChatSessionEntity;
import com.zhishiyun.kb.entity.KbChunkEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.mapper.BizContactMapper;
import com.zhishiyun.kb.mapper.ChatCitationMapper;
import com.zhishiyun.kb.mapper.ChatMessageMapper;
import com.zhishiyun.kb.mapper.ChatSessionMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.client.LlmClient;
import com.zhishiyun.kb.filter.TraceIdFilter;
import com.zhishiyun.kb.service.VectorSearchService.SearchHit;
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
    private final LlmClient llmClient;
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
     * 问答主流程：落用户消息 → 检索 → 推送 citation/thinking/delta → 落助手消息与引用。
     * SSE 事件：meta / citation / thinking / delta / done / error。
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
                    "phase", "search",
                    "message", "正在理解问题并检索知识库…",
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

            send(emitter, "meta", mapOf(
                    "status", "RECOGNIZING",
                    "phase", "recognize",
                    "message", "已识别 " + selected.size() + " 个相关片段",
                    "hitCount", selected.size()));

            List<ChatCitationEntity> citations = new ArrayList<ChatCitationEntity>();
            StringBuilder thinkingBuf = new StringBuilder();
            int idx = 1;
            for (SearchHit hit : selected) {
                KbChunkEntity chunk = hit.getChunk();
                KbDocumentEntity doc = kbDocumentMapper.selectById(chunk.getDocId());
                KbLibraryEntity library = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                        .eq(KbLibraryEntity::getCode, chunk.getLibraryCode()).last("limit 1"));
                String title = doc == null ? "未知文档" : doc.getTitle();
                Integer page = chunk.getPageNo();
                send(emitter, "citation", mapOf(
                        "index", idx,
                        "docId", String.valueOf(chunk.getDocId()),
                        "title", title,
                        "page", page,
                        "knowledgeBase", library == null ? chunk.getLibraryCode() : library.getName(),
                        "knowledgeBaseId", chunk.getLibraryCode(),
                        "excerpt", excerpt(chunk.getContent())));
                String recognizeLine = "识别来源 [" + idx + "] 《" + title + "》第 " + page + " 页\n";
                thinkingBuf.append(recognizeLine);
                send(emitter, "thinking", mapOf("content", recognizeLine));
                ChatCitationEntity cite = new ChatCitationEntity();
                cite.setCiteIndex(idx++);
                cite.setDocId(chunk.getDocId());
                cite.setTitle(title);
                cite.setPageNo(page);
                cite.setLibraryName(library == null ? chunk.getLibraryCode() : library.getName());
                cite.setLibraryCode(chunk.getLibraryCode());
                cite.setExcerpt(excerpt(chunk.getContent()));
                citations.add(cite);
            }

            send(emitter, "meta", mapOf(
                    "status", "THINKING",
                    "phase", "think",
                    "message", "正在分析检索片段并组织回答…"));
            String thinkGuide = "结合上述片段核对关键事实，按类别整理要点，并在句末标注引用编号。\n";
            thinkingBuf.append(thinkGuide);
            send(emitter, "thinking", mapOf("content", thinkGuide));

            AnswerFollowupSplitter splitter = new AnswerFollowupSplitter();
            boolean[] generating = {false};
            streamAnswer(question, selected, (type, content) -> {
                if ("thinking".equals(type)) {
                    thinkingBuf.append(content);
                    send(emitter, "thinking", mapOf("content", content));
                    return;
                }
                if (!generating[0]) {
                    generating[0] = true;
                    send(emitter, "meta", mapOf(
                            "status", "GENERATING",
                            "phase", "answer",
                            "message", "正在生成回答…"));
                }
                splitter.onChunk(content, part -> send(emitter, "delta", mapOf("content", part)));
            });
            splitter.finish(part -> send(emitter, "delta", mapOf("content", part)));

            String answer = splitter.answerText();
            List<String> followUps = splitter.followUps(3);
            ChatMessageEntity aiMsg = new ChatMessageEntity();
            aiMsg.setSessionId(sessionId);
            aiMsg.setRole(MessageRole.assistant.name());
            aiMsg.setAnswerStatus(AnswerStatus.OK.name());
            aiMsg.setContent(answer);
            aiMsg.setThinkingContent(thinkingBuf.toString());
            aiMsg.setFollowUps(toJsonArray(followUps));
            aiMsg.setElapsedMs((int) (System.currentTimeMillis() - start));
            chatMessageMapper.insert(aiMsg);
            for (ChatCitationEntity cite : citations) {
                cite.setMessageId(aiMsg.getId());
                chatCitationMapper.insert(cite);
            }
            java.util.Map<String, Object> done = mapOf(
                    "messageId", String.valueOf(aiMsg.getId()),
                    "elapsedMs", System.currentTimeMillis() - start,
                    "status", "OK",
                    "disclaimer", "AI 可能出错，请以原文为准");
            if (!followUps.isEmpty()) {
                done.put("followUps", followUps);
            }
            send(emitter, "done", done);
            emitter.complete();
        } catch (Exception e) {
            log.error("ask stream failed", e);
            try {
                int code = 50001;
                String message = e.getMessage();
                if (e instanceof com.zhishiyun.kb.common.BizException) {
                    com.zhishiyun.kb.common.BizException biz = (com.zhishiyun.kb.common.BizException) e;
                    code = biz.getCode();
                    message = biz.getMessage();
                }
                send(emitter, "error", mapOf("code", code, "message",
                        message == null || message.trim().isEmpty() ? "系统错误" : message));
            } catch (Exception ignored) {
            }
            emitter.complete();
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

    /** 基于检索片段流式调用 LLM 生成可溯源回答（同请求附带推荐追问）。 */
    private void streamAnswer(String question, List<SearchHit> hits, LlmClient.StreamSink sink) throws Exception {
        StringBuilder context = new StringBuilder();
        int idx = 1;
        for (SearchHit hit : hits) {
            context.append("[").append(idx++).append("] ")
                    .append(contextSnippet(hit.getChunk().getContent()))
                    .append("\n");
        }
        String system = "你是企业知识库助手。请仅依据给定检索片段回答用户问题。"
                + "输出要求：使用清晰层级与要点列表；关键事实句末用 [n] 标注引用来源；"
                + "不要编造片段中不存在的事实；若片段不足以回答，请明确说明。"
                + "不要输出与问题无关的开场白。"
                + "正文回答结束后，必须另起一行严格按下列格式输出 3 条推荐追问（不要多写说明文字）：\n"
                + AnswerFollowupSplitter.START + "\n"
                + "追问1\n追问2\n追问3\n"
                + AnswerFollowupSplitter.END + "\n"
                + "推荐追问必须紧扣用户当前问题与你刚给出的回答，具体可直接作为下一轮提问；"
                + "禁止随机、泛泛或与原问题重复。";
        String user = "用户问题：\n" + (question == null ? "" : question)
                + "\n\n检索片段：\n" + context;
        llmClient.chatStream(system, user, sink);
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? java.util.Collections.emptyList() : items);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String contextSnippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() > 800 ? t.substring(0, 800) : t;
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
