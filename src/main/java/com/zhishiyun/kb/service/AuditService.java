package com.zhishiyun.kb.service;

import com.zhishiyun.kb.entity.AuditLogEntity;
import com.zhishiyun.kb.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 统一审计写入。 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public void write(Long userId, String action, String targetType, String targetId, String detail, String ip) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setUserId(userId);
        audit.setAction(action);
        audit.setTargetType(targetType);
        audit.setTargetId(targetId);
        audit.setDetail(StringUtils.hasText(detail) ? detail : action);
        audit.setIp(ip);
        audit.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }

    public void write(Long userId, String action, String targetType, String targetId, String detail) {
        write(userId, action, targetType, targetId, detail, null);
    }

    public void write(Long userId, String action) {
        write(userId, action, "auth", String.valueOf(userId), action, null);
    }
}
