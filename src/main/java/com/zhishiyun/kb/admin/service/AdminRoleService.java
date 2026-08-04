package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.SysConfigEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.SysConfigMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.service.AuditService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色权限矩阵：三角色（EMPLOYEE / KB_ADMIN / SYS_ADMIN）的能力开关，存于 sys_config。
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    /** sys_config 中角色矩阵的键名。 */
    public static final String CONFIG_KEY = "admin.role.matrix";

    /** 可配置的权限位清单。 */
    private static final List<String> PERMS = Arrays.asList(
            "admin.access",
            "library.read", "library.write",
            "ingest.upload", "ingest.reindex",
            "acl.manage",
            "user.manage", "user.approve",
            "role.manage",
            "model.manage",
            "audit.read");

    private final SysConfigMapper sysConfigMapper;
    private final SysUserMapper sysUserMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /** 返回三角色卡片：名称、说明、人数、权限快照。 */
    public List<Map<String, Object>> listRoles() {
        Map<String, Map<String, Boolean>> matrix = loadMatrix();
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(roleCard(AdminAuthHelper.ROLE_EMPLOYEE, "普通员工", "仅用户端；无管理后台", matrix));
        list.add(roleCard(AdminAuthHelper.ROLE_KB_ADMIN, "知识管理员", "知识库/入库/ACL/知识相关审计", matrix));
        list.add(roleCard(AdminAuthHelper.ROLE_SYS_ADMIN, "系统管理员", "全部管理能力", matrix));
        return list;
    }

    public Map<String, Boolean> getPermissions(String roleCode) {
        Map<String, Map<String, Boolean>> matrix = loadMatrix();
        Map<String, Boolean> perms = matrix.get(roleCode);
        if (perms == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知角色");
        }
        return perms;
    }

    @Transactional
    public Map<String, Boolean> savePermissions(Long actorId, String roleCode, Map<String, Boolean> body) {
        if (!Arrays.asList(AdminAuthHelper.ROLE_EMPLOYEE, AdminAuthHelper.ROLE_KB_ADMIN, AdminAuthHelper.ROLE_SYS_ADMIN)
                .contains(roleCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知角色");
        }
        Map<String, Map<String, Boolean>> matrix = loadMatrix();
        Map<String, Boolean> normalized = defaultFor(roleCode);
        if (body != null) {
            for (String key : PERMS) {
                if (body.containsKey(key)) {
                    normalized.put(key, Boolean.TRUE.equals(body.get(key)));
                }
            }
        }
        // 硬约束：员工不可进后台；系统管理员权限位始终全开
        if (AdminAuthHelper.ROLE_EMPLOYEE.equals(roleCode)) {
            normalized.put("admin.access", false);
        }
        if (AdminAuthHelper.ROLE_SYS_ADMIN.equals(roleCode)) {
            for (String key : PERMS) {
                normalized.put(key, true);
            }
        }
        matrix.put(roleCode, normalized);
        saveMatrix(matrix);
        auditService.write(actorId, "ROLE_UPDATE", "system", roleCode, "更新角色权限矩阵");
        return normalized;
    }

    private Map<String, Object> roleCard(String code, String name, String desc, Map<String, Map<String, Boolean>> matrix) {
        long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getRoleCode, code));
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("code", code);
        m.put("name", name);
        m.put("description", desc);
        m.put("userCount", count);
        m.put("permissions", matrix.get(code));
        return m;
    }

    private Map<String, Map<String, Boolean>> loadMatrix() {
        SysConfigEntity cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                .eq(SysConfigEntity::getConfigKey, CONFIG_KEY)
                .last("limit 1"));
        if (cfg != null && cfg.getConfigValue() != null) {
            try {
                return objectMapper.readValue(cfg.getConfigValue(), new TypeReference<Map<String, Map<String, Boolean>>>() {
                });
            } catch (Exception ignored) {
            }
        }
        Map<String, Map<String, Boolean>> defaults = new LinkedHashMap<String, Map<String, Boolean>>();
        defaults.put(AdminAuthHelper.ROLE_EMPLOYEE, defaultFor(AdminAuthHelper.ROLE_EMPLOYEE));
        defaults.put(AdminAuthHelper.ROLE_KB_ADMIN, defaultFor(AdminAuthHelper.ROLE_KB_ADMIN));
        defaults.put(AdminAuthHelper.ROLE_SYS_ADMIN, defaultFor(AdminAuthHelper.ROLE_SYS_ADMIN));
        return defaults;
    }

    private void saveMatrix(Map<String, Map<String, Boolean>> matrix) {
        try {
            String json = objectMapper.writeValueAsString(matrix);
            SysConfigEntity cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigEntity>()
                    .eq(SysConfigEntity::getConfigKey, CONFIG_KEY)
                    .last("limit 1"));
            if (cfg == null) {
                cfg = new SysConfigEntity();
                cfg.setConfigKey(CONFIG_KEY);
                cfg.setConfigValue(json);
                cfg.setCreatedAt(LocalDateTime.now());
                cfg.setUpdatedAt(LocalDateTime.now());
                sysConfigMapper.insert(cfg);
            } else {
                cfg.setConfigValue(json);
                cfg.setUpdatedAt(LocalDateTime.now());
                sysConfigMapper.updateById(cfg);
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "保存角色矩阵失败");
        }
    }

    /** 内置默认矩阵：员工全关；知识管理员开放知识相关；系统管理员全开。 */
    private Map<String, Boolean> defaultFor(String role) {
        Map<String, Boolean> m = new LinkedHashMap<String, Boolean>();
        for (String p : PERMS) {
            m.put(p, false);
        }
        if (AdminAuthHelper.ROLE_EMPLOYEE.equals(role)) {
            return m;
        }
        if (AdminAuthHelper.ROLE_KB_ADMIN.equals(role)) {
            m.put("admin.access", true);
            m.put("library.read", true);
            m.put("library.write", true);
            m.put("ingest.upload", true);
            m.put("ingest.reindex", true);
            m.put("acl.manage", true);
            m.put("audit.read", true);
            return m;
        }
        for (String p : PERMS) {
            m.put(p, true);
        }
        return m;
    }
}
