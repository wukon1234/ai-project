package com.zhishiyun.kb.service;

import com.zhishiyun.kb.entity.AuditLogEntity;
import com.zhishiyun.kb.mapper.AuditLogMapper;
import com.zhishiyun.kb.util.ClientIpUtils;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 统一审计写入。 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Map<String, String> ACTION_DETAIL;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("LOGIN", "用户登录成功");
        m.put("LOGIN_FAIL", "用户登录失败");
        m.put("SSO_LOGIN", "SSO 登录成功");
        m.put("PASSWORD_RESET", "用户重置密码");
        m.put("DOWNLOAD", "下载文档");
        m.put("DOWNLOAD_DOC", "下载文档");
        m.put("PREVIEW_DOC", "预览文档");
        m.put("SHARE", "分享文档");
        m.put("SHARE_DOC", "分享文档");
        m.put("SHARE_SESSION", "分享会话");
        m.put("AUTH_DENY", "鉴权拒绝");
        ACTION_DETAIL = Collections.unmodifiableMap(m);
    }

    private final AuditLogMapper auditLogMapper;

    public void write(Long userId, String action, String targetType, String targetId, String detail, String ip) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setUserId(userId);
        audit.setAction(action);
        audit.setTargetType(targetType);
        audit.setTargetId(targetId);
        audit.setDetail(resolveDetail(action, detail));
        audit.setIp(StringUtils.hasText(ip) ? ip : ClientIpUtils.current());
        audit.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }

    public void write(Long userId, String action, String targetType, String targetId, String detail) {
        write(userId, action, targetType, targetId, detail, null);
    }

    public void write(Long userId, String action) {
        write(userId, action, "auth", String.valueOf(userId), ACTION_DETAIL.get(action), null);
    }

    public static String defaultDetail(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        String mapped = ACTION_DETAIL.get(action);
        return mapped != null ? mapped : action;
    }

    private static String resolveDetail(String action, String detail) {
        if (StringUtils.hasText(detail) && !detail.equals(action)) {
            return detail;
        }
        return defaultDetail(action);
    }
}
