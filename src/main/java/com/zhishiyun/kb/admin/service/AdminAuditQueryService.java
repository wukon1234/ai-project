package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.dto.AdminAuditRecord;
import com.zhishiyun.kb.common.PageResult;
import com.zhishiyun.kb.entity.AuditLogEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.AuditLogMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.model.AuthUser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> KB_ACTIONS = new HashSet<String>(Arrays.asList(
            "INGEST_UPLOAD", "INGEST_RETRY", "INGEST_REINDEX", "ACL_UPDATE", "LIBRARY_CREATE", "LIBRARY_UPDATE",
            "DOWNLOAD_DOC", "PREVIEW_DOC", "SHARE_DOC", "SHARE_SESSION", "DOWNLOAD", "SHARE"));

    private final AuditLogMapper auditLogMapper;
    private final SysUserMapper sysUserMapper;

    public List<AdminAuditRecord> recent(AuthUser viewer, int limit) {
        PageResult<AdminAuditRecord> page = query(viewer, null, null, null, null, null, null, null, 1, limit);
        return page.getRecords();
    }

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
        if (StringUtils.hasText(keyword)) {
            q.like(AuditLogEntity::getDetail, keyword);
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
        List<AdminAuditRecord> filtered = new ArrayList<AdminAuditRecord>();
        for (AuditLogEntity row : all) {
            AdminAuditRecord record = toRecord(row);
            if (StringUtils.hasText(actor)) {
                String a = actor.toLowerCase();
                boolean match = (record.getActor() != null && record.getActor().toLowerCase().contains(a))
                        || (record.getActorEmail() != null && record.getActorEmail().toLowerCase().contains(a));
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

    public byte[] exportCsv(AuthUser viewer, String range, String from, String to, String actor,
                            String actions, String targetType, String keyword) {
        PageResult<AdminAuditRecord> page = query(viewer, range, from, to, actor, actions, targetType, keyword, 1, 5000);
        StringBuilder sb = new StringBuilder();
        sb.append("createdAt,actor,actorEmail,action,targetType,targetId,detail,ip\n");
        for (AdminAuditRecord r : page.getRecords()) {
            sb.append(csv(r.getCreatedAt())).append(',')
                    .append(csv(r.getActor())).append(',')
                    .append(csv(r.getActorEmail())).append(',')
                    .append(csv(r.getAction())).append(',')
                    .append(csv(r.getTargetType())).append(',')
                    .append(csv(r.getTargetId())).append(',')
                    .append(csv(r.getDetail())).append(',')
                    .append(csv(r.getIp())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private AdminAuditRecord toRecord(AuditLogEntity row) {
        String actor = "";
        String email = "";
        if (row.getUserId() != null) {
            SysUserEntity user = sysUserMapper.selectById(row.getUserId());
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
        return AdminAuditRecord.builder()
                .id(String.valueOf(row.getId()))
                .actor(actor)
                .actorEmail(email)
                .action(action)
                .target(row.getTargetId())
                .targetType(row.getTargetType())
                .targetId(row.getTargetId())
                .detail(row.getDetail())
                .ip(row.getIp())
                .createdAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(FMT))
                .knowledgeRelated(knowledge)
                .build();
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
