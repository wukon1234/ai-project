package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.dto.AdminLibraryCreateRequest;
import com.zhishiyun.kb.admin.dto.AdminLibraryRecord;
import com.zhishiyun.kb.admin.dto.AdminLibraryUpdateRequest;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.KbAclEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.service.AuditService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理后台知识库 CRUD：维护 code/名称/标签/全员可读等元数据。
 */
@Service
@RequiredArgsConstructor
public class AdminLibraryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KbLibraryMapper kbLibraryMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbAclMapper kbAclMapper;
    private final AuditService auditService;

    /** 列出知识库；keyword 匹配 name 或 code。 */
    public List<AdminLibraryRecord> list(String keyword) {
        LambdaQueryWrapper<KbLibraryEntity> q = new LambdaQueryWrapper<KbLibraryEntity>()
                .orderByAsc(KbLibraryEntity::getId);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(KbLibraryEntity::getName, keyword).or().like(KbLibraryEntity::getCode, keyword));
        }
        return kbLibraryMapper.selectList(q).stream().map(this::toRecord).collect(Collectors.toList());
    }

    @Transactional
    public AdminLibraryRecord create(Long actorId, AdminLibraryCreateRequest req) {
        Long exists = kbLibraryMapper.selectCount(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getCode, req.getCode()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库 code 已存在");
        }
        KbLibraryEntity entity = new KbLibraryEntity();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setTags(joinTags(req.getTags()));
        entity.setDocCount(0);
        entity.setPublicRead(Boolean.TRUE.equals(req.getPublicRead()) ? 1 : 0);
        kbLibraryMapper.insert(entity);
        auditService.write(actorId, "LIBRARY_CREATE", "library", req.getCode(), "创建知识库 " + req.getName());
        return toRecord(entity);
    }

    @Transactional
    public AdminLibraryRecord update(Long actorId, String code, AdminLibraryUpdateRequest req) {
        KbLibraryEntity entity = requireByCode(code);
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setTags(joinTags(req.getTags()));
        if (req.getPublicRead() != null) {
            entity.setPublicRead(Boolean.TRUE.equals(req.getPublicRead()) ? 1 : 0);
        }
        kbLibraryMapper.updateById(entity);
        auditService.write(actorId, "LIBRARY_UPDATE", "library", code, "更新知识库 " + req.getName());
        return toRecord(kbLibraryMapper.selectById(entity.getId()));
    }

    /**
     * 删除知识库：仅允许空库（无文档）；同时清理该库 ACL。
     */
    @Transactional
    public void delete(Long actorId, String code) {
        KbLibraryEntity entity = requireByCode(code);
        long docCount = kbDocumentMapper.selectCount(new LambdaQueryWrapper<KbDocumentEntity>()
                .eq(KbDocumentEntity::getLibraryCode, code));
        if (docCount > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "库内仍有 " + docCount + " 份文档，请先清空后再删除知识库");
        }
        // 按 library_id / library_code 双条件清理，避免历史脏数据残留
        kbAclMapper.delete(new LambdaQueryWrapper<KbAclEntity>()
                .eq(KbAclEntity::getLibraryId, entity.getId())
                .or()
                .eq(KbAclEntity::getLibraryCode, code));
        int removed = kbLibraryMapper.deleteById(entity.getId());
        if (removed <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库删除失败或不存在");
        }
        auditService.write(actorId, "LIBRARY_DELETE", "library", code, "删除知识库 " + entity.getName());
    }

    /** 按 code 加载知识库，不存在则抛业务异常。 */
    public KbLibraryEntity requireByCode(String code) {
        KbLibraryEntity entity = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getCode, code)
                .last("limit 1"));
        if (entity == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库不存在");
        }
        return entity;
    }

    private AdminLibraryRecord toRecord(KbLibraryEntity lib) {
        long docCount = kbDocumentMapper.selectCount(new LambdaQueryWrapper<KbDocumentEntity>()
                .eq(KbDocumentEntity::getLibraryCode, lib.getCode()));
        return AdminLibraryRecord.builder()
                .id(String.valueOf(lib.getId()))
                .code(lib.getCode())
                .name(lib.getName())
                .description(lib.getDescription())
                .tags(parseTags(lib.getTags()))
                .docCount((int) docCount)
                .updatedAt(lib.getUpdatedAt() == null ? null : lib.getUpdatedAt().format(FMT))
                .publicRead(lib.getPublicRead() != null && lib.getPublicRead() == 1)
                .build();
    }

    /** 标签统一加 # 前缀后空格拼接入库。 */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        List<String> normalized = new ArrayList<String>();
        for (String t : tags) {
            if (!StringUtils.hasText(t)) {
                continue;
            }
            String v = t.trim();
            normalized.add(v.startsWith("#") ? v : "#" + v);
        }
        return String.join(" ", normalized);
    }

    private List<String> parseTags(String raw) {
        List<String> tags = new ArrayList<String>();
        if (!StringUtils.hasText(raw)) {
            return tags;
        }
        for (String part : raw.split("[,，\\s]+")) {
            if (StringUtils.hasText(part)) {
                tags.add(part.startsWith("#") ? part : "#" + part);
            }
        }
        return tags;
    }
}
