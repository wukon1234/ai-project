package com.zhishiyun.kb.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.document.DocumentService;
import com.zhishiyun.kb.favorite.dto.FavoriteAnswerRequest;
import com.zhishiyun.kb.favorite.dto.FavoriteDocumentRequest;
import com.zhishiyun.kb.infra.mysql.entity.ChatCitationEntity;
import com.zhishiyun.kb.infra.mysql.entity.ChatMessageEntity;
import com.zhishiyun.kb.infra.mysql.entity.FavAnswerEntity;
import com.zhishiyun.kb.infra.mysql.entity.FavDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbDocumentEntity;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.entity.UsageEventEntity;
import com.zhishiyun.kb.infra.mysql.mapper.ChatCitationMapper;
import com.zhishiyun.kb.infra.mysql.mapper.ChatMessageMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavAnswerMapper;
import com.zhishiyun.kb.infra.mysql.mapper.FavDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbDocumentMapper;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UsageEventMapper;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 收藏：文档收藏与回答收藏。 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FavDocumentMapper favDocumentMapper;
    private final FavAnswerMapper favAnswerMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatCitationMapper chatCitationMapper;
    private final UsageEventMapper usageEventMapper;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    /** 文档收藏列表。 */
    public List<Map<String, Object>> listDocs(Long userId) {
        List<FavDocumentEntity> favs = favDocumentMapper.selectList(new LambdaQueryWrapper<FavDocumentEntity>()
                .eq(FavDocumentEntity::getUserId, userId)
                .orderByDesc(FavDocumentEntity::getSavedAt));
        return favs.stream().map(f -> {
            KbDocumentEntity d = kbDocumentMapper.selectById(f.getDocId());
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("id", f.getId());
            m.put("docId", f.getDocId());
            m.put("page", f.getPageNo() == null ? 1 : f.getPageNo());
            m.put("title", d == null ? "未知文档" : d.getTitle());
            m.put("category", d == null ? null : d.getCategory());
            m.put("knowledgeBase", resolveLibraryName(d));
            m.put("savedAt", f.getSavedAt() == null ? null : f.getSavedAt().format(FMT));
            return m;
        }).collect(Collectors.toList());
    }

    /** 收藏文档（幂等）。 */
    @Transactional
    public void saveDoc(Long userId, FavoriteDocumentRequest request) {
        documentService.getPermittedDocument(userId, request.getDocId());
        FavDocumentEntity existing = favDocumentMapper.selectOne(new LambdaQueryWrapper<FavDocumentEntity>()
                .eq(FavDocumentEntity::getUserId, userId)
                .eq(FavDocumentEntity::getDocId, request.getDocId())
                .last("limit 1"));
        if (existing == null) {
            existing = new FavDocumentEntity();
            existing.setUserId(userId);
            existing.setDocId(request.getDocId());
            existing.setPageNo(request.getPage() == null ? 1 : request.getPage());
            favDocumentMapper.insert(existing);
        } else {
            existing.setPageNo(request.getPage() == null ? existing.getPageNo() : request.getPage());
            favDocumentMapper.updateById(existing);
        }
        usage(userId, "FAVORITE", String.valueOf(request.getDocId()));
    }

    /** 取消文档收藏。 */
    @Transactional
    public void deleteDoc(Long userId, Long docId) {
        favDocumentMapper.delete(new LambdaQueryWrapper<FavDocumentEntity>()
                .eq(FavDocumentEntity::getUserId, userId)
                .eq(FavDocumentEntity::getDocId, docId));
    }

    /** 回答收藏列表。 */
    public List<Map<String, Object>> listAnswers(Long userId) {
        return favAnswerMapper.selectList(new LambdaQueryWrapper<FavAnswerEntity>()
                        .eq(FavAnswerEntity::getUserId, userId)
                        .orderByDesc(FavAnswerEntity::getSavedAt))
                .stream().map(a -> {
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("id", a.getId());
                    m.put("messageId", a.getMessageId());
                    m.put("summary", a.getSummary());
                    m.put("topic", a.getTopic());
                    m.put("source", a.getSourceText() == null ? "知识库回答" : a.getSourceText());
                    m.put("savedAt", a.getSavedAt() == null ? null : a.getSavedAt().format(FMT));
                    m.put("context", parseContext(a.getContextJson()));
                    return m;
                }).collect(Collectors.toList());
    }

    /** 收藏助手回答。 */
    @Transactional
    public void saveAnswer(Long userId, FavoriteAnswerRequest request) {
        ChatMessageEntity msg = chatMessageMapper.selectById(request.getMessageId());
        if (msg == null || !"assistant".equals(msg.getRole())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "messageId 无效");
        }
        FavAnswerEntity existing = favAnswerMapper.selectOne(new LambdaQueryWrapper<FavAnswerEntity>()
                .eq(FavAnswerEntity::getUserId, userId)
                .eq(FavAnswerEntity::getMessageId, request.getMessageId())
                .last("limit 1"));
        if (existing == null) {
            existing = new FavAnswerEntity();
            existing.setUserId(userId);
            existing.setMessageId(request.getMessageId());
            existing.setSummary(request.getSummary() == null ? truncate(msg.getContent(), 200) : request.getSummary());
            existing.setTopic(request.getTopic() == null ? "收藏回答" : request.getTopic());
            existing.setSourceText(resolveAnswerSource(msg));
            existing.setContextJson(buildContextJson(msg));
            favAnswerMapper.insert(existing);
        } else {
            existing.setSummary(request.getSummary() == null ? existing.getSummary() : request.getSummary());
            existing.setTopic(request.getTopic() == null ? existing.getTopic() : request.getTopic());
            favAnswerMapper.updateById(existing);
        }
        usage(userId, "FAVORITE", String.valueOf(request.getMessageId()));
    }

    /** 取消回答收藏。 */
    @Transactional
    public void deleteAnswer(Long userId, Long id) {
        favAnswerMapper.delete(new LambdaQueryWrapper<FavAnswerEntity>()
                .eq(FavAnswerEntity::getUserId, userId)
                .eq(FavAnswerEntity::getId, id));
    }

    /** 收藏埋点。 */
    private void usage(Long userId, String event, String refId) {
        UsageEventEntity u = new UsageEventEntity();
        u.setUserId(userId);
        u.setEventType(event);
        u.setRefId(refId);
        usageEventMapper.insert(u);
    }

    private String resolveLibraryName(KbDocumentEntity doc) {
        if (doc == null || !StringUtils.hasText(doc.getLibraryCode())) {
            return "";
        }
        KbLibraryEntity lib = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getCode, doc.getLibraryCode())
                .last("limit 1"));
        return lib == null ? doc.getLibraryCode() : lib.getName();
    }

    private String resolveAnswerSource(ChatMessageEntity msg) {
        ChatCitationEntity cite = chatCitationMapper.selectOne(new LambdaQueryWrapper<ChatCitationEntity>()
                .eq(ChatCitationEntity::getMessageId, msg.getId())
                .orderByAsc(ChatCitationEntity::getCiteIndex)
                .last("limit 1"));
        if (cite == null) {
            return "知识库回答";
        }
        String title = cite.getTitle() == null ? "未知文档" : cite.getTitle();
        Integer page = cite.getPageNo() == null ? 1 : cite.getPageNo();
        return title + " · 第 " + page + " 页";
    }

    private String buildContextJson(ChatMessageEntity assistant) {
        List<String> lines = new ArrayList<String>();
        ChatMessageEntity userMsg = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, assistant.getSessionId())
                .eq(ChatMessageEntity::getRole, "user")
                .lt(ChatMessageEntity::getId, assistant.getId())
                .orderByDesc(ChatMessageEntity::getId)
                .last("limit 1"));
        if (userMsg != null && StringUtils.hasText(userMsg.getContent())) {
            lines.add("问：" + truncate(userMsg.getContent(), 300));
        }
        lines.add("答：" + truncate(assistant.getContent(), 500));
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseContext(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<String>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return Arrays.asList(json);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
