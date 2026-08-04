package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.dto.AdminAuditRecord;
import com.zhishiyun.kb.common.PageResult;
import com.zhishiyun.kb.entity.AuditLogEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.AuditLogMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.model.AuthUser;
import com.zhishiyun.kb.service.AuditService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理后台审计查询：按时间范围/操作人/动作过滤，解析展示字段，并支持 CSV 导出。
 * <p>非 SYS_ADMIN 自动限制为 {@link #KB_ACTIONS} 知识相关动作。
 */
@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** KB_ADMIN 可见的动作白名单。 */
    private static final Set<String> KB_ACTIONS = new HashSet<String>(Arrays.asList(
            "INGEST_UPLOAD", "INGEST_RETRY", "INGEST_REINDEX", "ACL_UPDATE", "LIBRARY_CREATE", "LIBRARY_UPDATE",
            "DOWNLOAD_DOC", "PREVIEW_DOC", "SHARE_DOC", "SHARE_SESSION", "DOWNLOAD", "SHARE"));

    private static final Map<String, String> ACTION_LABELS;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("LOGIN", "登录");
        m.put("LOGIN_FAIL", "登录失败");
        m.put("SSO_LOGIN", "SSO 登录");
        m.put("PASSWORD_RESET", "重置密码");
        m.put("DOWNLOAD", "下载");
        m.put("DOWNLOAD_DOC", "下载文档");
        m.put("PREVIEW_DOC", "预览文档");
        m.put("SHARE", "分享");
        m.put("SHARE_DOC", "分享文档");
        m.put("SHARE_SESSION", "分享会话");
        m.put("AUTH_DENY", "鉴权拒绝");
        m.put("USER_CREATE", "创建用户");
        m.put("USER_APPROVE", "通过审核");
        m.put("USER_REJECT", "拒绝审核");
        m.put("USER_DISABLE", "禁用用户");
        m.put("USER_ENABLE", "启用用户");
        m.put("USER_RESET_PASSWORD", "重置密码");
        m.put("ACL_UPDATE", "更新权限");
        m.put("INGEST_UPLOAD", "文档入库");
        m.put("INGEST_RETRY", "重试入库");
        m.put("INGEST_REINDEX", "重建索引");
        m.put("MODEL_UPDATE", "更新模型");
        m.put("ROLE_UPDATE", "更新角色");
        m.put("LIBRARY_CREATE", "创建知识库");
        m.put("LIBRARY_UPDATE", "更新知识库");
        ACTION_LABELS = Collections.unmodifiableMap(m);
    }

    private final AuditLogMapper auditLogMapper;
    private final SysUserMapper sysUserMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbLibraryMapper kbLibraryMapper;

    /** 仪表盘用：按当前视角权限取最近审计。 */
    public List<AdminAuditRecord> recent(AuthUser viewer, int limit) {
        PageResult<AdminAuditRecord> page = query(viewer, null, null, null, null, null, null, null, 1, limit);
        return page.getRecords();
    }

    /**
     * 审计分页查询。
     * @param range today / 7d / 30d / custom；custom 时配合 from、to（yyyy-MM-dd）
     * @param actions 逗号分隔的动作码，可空
     */
    public PageResult<AdminAuditRecord> query(
            AuthUser viewer,
            String range,
            String from,
            String to,
            String actor,
            String actions,
            String targetType,
            String keyword,
            int page,
            int size) {
        int pageNum = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        LocalDateTime start = null;
        LocalDateTime end = null;
        if ("today".equalsIgnoreCase(range)) {
            start = LocalDate.now().atStartOfDay();
            end = LocalDateTime.now();
        } else if ("7d".equalsIgnoreCase(range)) {
            start = LocalDateTime.now().minusDays(7);
            end = LocalDateTime.now();
        } else if ("30d".equalsIgnoreCase(range)) {
            start = LocalDateTime.now().minusDays(30);
            end = LocalDateTime.now();
        } else if ("custom".equalsIgnoreCase(range)) {
            if (StringUtils.hasText(from)) {
                start = LocalDate.parse(from).atStartOfDay();
            }
            if (StringUtils.hasText(to)) {
                end = LocalDate.parse(to).atTime(LocalTime.MAX);
            }
        }

        LambdaQueryWrapper<AuditLogEntity> q = new LambdaQueryWrapper<AuditLogEntity>()
                .orderByDesc(AuditLogEntity::getCreatedAt);
        if (start != null) {
            q.ge(AuditLogEntity::getCreatedAt, start);
        }
        if (end != null) {
            q.le(AuditLogEntity::getCreatedAt, end);
        }
        if (StringUtils.hasText(targetType)) {
            q.eq(AuditLogEntity::getTargetType, targetType);
        }
        if (StringUtils.hasText(actions)) {
            List<String> actionList = new ArrayList<String>();
            for (String a : actions.split(",")) {
                if (StringUtils.hasText(a)) {
                    actionList.add(a.trim());
                }
            }
            if (!actionList.isEmpty()) {
                q.in(AuditLogEntity::getAction, actionList);
            }
        }
        if (!AdminAuthHelper.isSysAdmin(viewer)) {
            q.in(AuditLogEntity::getAction, KB_ACTIONS);
        }

        List<AuditLogEntity> all = auditLogMapper.selectList(q);
        Map<Long, SysUserEntity> userCache = new HashMap<Long, SysUserEntity>();
        Map<Long, KbDocumentEntity> docCache = new HashMap<Long, KbDocumentEntity>();
        Map<String, KbLibraryEntity> libCache = new HashMap<String, KbLibraryEntity>();

        List<AdminAuditRecord> filtered = new ArrayList<AdminAuditRecord>();
        for (AuditLogEntity row : all) {
            AdminAuditRecord record = toRecord(row, userCache, docCache, libCache);
            if (StringUtils.hasText(actor)) {
                String a = actor.toLowerCase();
                boolean match = (record.getActor() != null && record.getActor().toLowerCase().contains(a))
                        || (record.getActorEmail() != null && record.getActorEmail().toLowerCase().contains(a));
                if (!match) {
                    continue;
                }
            }
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.toLowerCase();
                boolean match = (record.getDetail() != null && record.getDetail().toLowerCase().contains(kw))
                        || (record.getTarget() != null && record.getTarget().toLowerCase().contains(kw))
                        || (record.getTargetId() != null && record.getTargetId().toLowerCase().contains(kw))
                        || (record.getAction() != null && record.getAction().toLowerCase().contains(kw));
                if (!match) {
                    continue;
                }
            }
            filtered.add(record);
        }
        long total = filtered.size();
        int fromIdx = Math.min((pageNum - 1) * pageSize, filtered.size());
        int toIdx = Math.min(fromIdx + pageSize, filtered.size());
        return new PageResult<AdminAuditRecord>(total, pageNum, pageSize, filtered.subList(fromIdx, toIdx));
    }

    /** 导出与 query 相同筛选条件的 CSV（上限 5000 条）。 */
    public byte[] exportCsv(AuthUser viewer, String range, String from, String to, String actor,
                            String actions, String targetType, String keyword) {
        PageResult<AdminAuditRecord> page = query(viewer, range, from, to, actor, actions, targetType, keyword, 1, 5000);
        StringBuilder sb = new StringBuilder();
        sb.append("时间,操作人,邮箱,操作类型,对象类型,对象,详情,IP\n");
        for (AdminAuditRecord r : page.getRecords()) {
            sb.append(csv(r.getCreatedAt())).append(',')
                    .append(csv(r.getActor())).append(',')
                    .append(csv(r.getActorEmail())).append(',')
                    .append(csv(actionLabel(r.getAction()))).append(',')
                    .append(csv(r.getTargetType())).append(',')
                    .append(csv(r.getTarget())).append(',')
                    .append(csv(r.getDetail())).append(',')
                    .append(csv(r.getIp())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private AdminAuditRecord toRecord(
            AuditLogEntity row,
            Map<Long, SysUserEntity> userCache,
            Map<Long, KbDocumentEntity> docCache,
            Map<String, KbLibraryEntity> libCache) {
        String actor = "";
        String email = "";
        if (row.getUserId() != null) {
            SysUserEntity user = loadUser(row.getUserId(), userCache);
            if (user != null) {
                actor = user.getName();
                email = user.getEmail();
            }
        }
        String action = row.getAction() == null ? "" : row.getAction();
        boolean knowledge = KB_ACTIONS.contains(action)
                || action.startsWith("INGEST_")
                || action.startsWith("ACL_")
                || action.startsWith("LIBRARY_");
        String target = resolveTarget(row, actor, userCache, docCache, libCache);
        String detail = resolveDetail(action, row.getDetail());
        return AdminAuditRecord.builder()
                .id(String.valueOf(row.getId()))
                .actor(StringUtils.hasText(actor) ? actor : "—")
                .actorEmail(email)
                .action(action)
                .target(target)
                .targetType(row.getTargetType())
                .targetId(row.getTargetId())
                .detail(detail)
                .ip(row.getIp())
                .createdAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(FMT))
                .knowledgeRelated(knowledge)
                .build();
    }

    private String resolveTarget(
            AuditLogEntity row,
            String actorName,
            Map<Long, SysUserEntity> userCache,
            Map<Long, KbDocumentEntity> docCache,
            Map<String, KbLibraryEntity> libCache) {
        String type = row.getTargetType() == null ? "" : row.getTargetType();
        String id = row.getTargetId();
        if (!StringUtils.hasText(id)) {
            return "—";
        }
        if ("auth".equals(type)) {
            if (StringUtils.hasText(actorName)) {
                return actorName;
            }
            return "账号认证";
        }
        if ("user".equals(type)) {
            try {
                Long uid = Long.valueOf(id);
                SysUserEntity user = loadUser(uid, userCache);
                if (user != null && StringUtils.hasText(user.getName())) {
                    return user.getName();
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
            return id;
        }
        if ("document".equals(type)) {
            try {
                Long docId = Long.valueOf(id);
                KbDocumentEntity doc = loadDoc(docId, docCache);
                if (doc != null && StringUtils.hasText(doc.getTitle())) {
                    return doc.getTitle();
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
            return "文档 #" + id;
        }
        if ("library".equals(type)) {
            KbLibraryEntity lib = loadLib(id, libCache);
            if (lib != null && StringUtils.hasText(lib.getName())) {
                return lib.getName();
            }
            return id;
        }
        if ("session".equals(type)) {
            return "会话 #" + id;
        }
        if ("acl".equals(type)) {
            return "权限规则 #" + id;
        }
        if ("system".equals(type)) {
            return StringUtils.hasText(id) ? id : "系统";
        }
        return id;
    }

    private String resolveDetail(String action, String detail) {
        if (!StringUtils.hasText(detail) || detail.equals(action)) {
            return AuditService.defaultDetail(action);
        }
        if (ACTION_LABELS.containsKey(detail) && detail.equals(action)) {
            return AuditService.defaultDetail(action);
        }
        return detail;
    }

    private String actionLabel(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        String label = ACTION_LABELS.get(action);
        return label != null ? label : action;
    }

    private SysUserEntity loadUser(Long id, Map<Long, SysUserEntity> cache) {
        if (id == null) {
            return null;
        }
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        SysUserEntity user = sysUserMapper.selectById(id);
        cache.put(id, user);
        return user;
    }

    private KbDocumentEntity loadDoc(Long id, Map<Long, KbDocumentEntity> cache) {
        if (id == null) {
            return null;
        }
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        KbDocumentEntity doc = kbDocumentMapper.selectById(id);
        cache.put(id, doc);
        return doc;
    }

    private KbLibraryEntity loadLib(String code, Map<String, KbLibraryEntity> cache) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        if (cache.containsKey(code)) {
            return cache.get(code);
        }
        KbLibraryEntity lib = kbLibraryMapper.selectOne(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getCode, code)
                .last("LIMIT 1"));
        cache.put(code, lib);
        return lib;
    }

    private String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
