package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 知识浏览：按 ACL 返回库树与文档列表。 */
@Service
@RequiredArgsConstructor
public class BrowseService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final LibraryAccessService libraryAccessService;
    private final KbLibraryMapper kbLibraryMapper;
    private final KbDocumentMapper kbDocumentMapper;

    /** 返回用户有权访问的知识库树。 */
    public List<Map<String, Object>> libraries(Long userId) {
        Set<String> scopes = libraryAccessService.accessibleLibraryCodes(userId);
        if (scopes.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }
        List<KbLibraryEntity> libraries = kbLibraryMapper.selectList(new LambdaQueryWrapper<KbLibraryEntity>()
                .in(KbLibraryEntity::getCode, scopes));
        List<Map<String, Object>> res = new ArrayList<Map<String, Object>>();
        for (KbLibraryEntity lib : libraries) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("code", lib.getCode());
            m.put("name", lib.getName());
            m.put("description", lib.getDescription());
            m.put("docCount", kbDocumentMapper.selectCount(new LambdaQueryWrapper<KbDocumentEntity>()
                    .eq(KbDocumentEntity::getLibraryCode, lib.getCode())));
            m.put("tags", parseTags(lib.getTags()));
            m.put("updatedAt", lib.getUpdatedAt() == null ? null : lib.getUpdatedAt().format(FMT));
            res.add(m);
        }
        return res;
    }

    /** 某库下文档分页列表（支持分类与关键词）。 */
    public Map<String, Object> libraryDocs(Long userId, String code, String category, String q, int page, int size) {
        if (!libraryAccessService.canRead(userId, code)) {
            throw new BizException(ErrorCode.FORBIDDEN_LIBRARY);
        }
        List<KbDocumentEntity> docs = kbDocumentMapper.selectList(new LambdaQueryWrapper<KbDocumentEntity>()
                .eq(KbDocumentEntity::getLibraryCode, code)
                .orderByDesc(KbDocumentEntity::getUpdatedAt));
        List<KbDocumentEntity> filtered = docs.stream().filter(d -> {
            boolean catOk = "all".equals(category) || category.equals(d.getCategory());
            boolean qOk = !StringUtils.hasText(q)
                    || (d.getTitle() != null && d.getTitle().toLowerCase().contains(q.toLowerCase()))
                    || (d.getSummary() != null && d.getSummary().toLowerCase().contains(q.toLowerCase()));
            return catOk && qOk;
        }).collect(Collectors.toList());
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(filtered.size(), from + size);
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        if (from < to) {
            for (KbDocumentEntity d : filtered.subList(from, to)) {
                Map<String, Object> m = new HashMap<String, Object>();
                m.put("id", String.valueOf(d.getId()));
                m.put("libraryId", code);
                m.put("title", d.getTitle());
                m.put("category", d.getCategory() == null ? "manual" : d.getCategory());
                m.put("pages", d.getPages() == null ? 0 : d.getPages());
                m.put("updatedAt", d.getUpdatedAt() == null ? null : d.getUpdatedAt().format(FMT));
                m.put("views", d.getViewCount() == null ? 0 : d.getViewCount());
                m.put("page", 1);
                m.put("summary", d.getSummary() == null ? "" : d.getSummary());
                list.add(m);
            }
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("page", page);
        data.put("size", size);
        data.put("total", filtered.size());
        data.put("list", list);
        return data;
    }

    private List<String> parseTags(String raw) {
        List<String> tags = new ArrayList<String>();
        if (!StringUtils.hasText(raw)) {
            return tags;
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            String inner = text.substring(1, text.endsWith("]") ? text.length() - 1 : text.length());
            for (String part : inner.split(",")) {
                String t = part.trim().replace("\"", "").replace("'", "");
                if (StringUtils.hasText(t)) {
                    tags.add(t.startsWith("#") ? t : "#" + t);
                }
            }
            return tags;
        }
        for (String part : text.split("[,，\\s]+")) {
            if (StringUtils.hasText(part)) {
                tags.add(part.startsWith("#") ? part : "#" + part);
            }
        }
        return tags;
    }
}
