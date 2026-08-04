package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.dto.AdminUserCreateRequest;
import com.zhishiyun.kb.admin.dto.AdminUserRecord;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.common.PageResult;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.entity.UserPreferenceEntity;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.mapper.UserPreferenceMapper;
import com.zhishiyun.kb.service.AuditService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理后台用户领域服务：查询、创建、审核、启停、改角色、重置密码。
 * <p>用户 status：0 待审 / 1 启用 / 2 禁用。关键写操作会记审计日志。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ROLES = new HashSet<String>(Arrays.asList(
            AdminAuthHelper.ROLE_EMPLOYEE, AdminAuthHelper.ROLE_KB_ADMIN, AdminAuthHelper.ROLE_SYS_ADMIN));

    private final SysUserMapper sysUserMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** 按状态/角色过滤后，再对姓名、工号、邮箱等做关键字内存过滤并分页。 */
    public PageResult<AdminUserRecord> list(Integer status, String role, String keyword, int page, int size) {
        int pageNum = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<SysUserEntity> q = new LambdaQueryWrapper<SysUserEntity>()
                .orderByDesc(SysUserEntity::getCreatedAt);
        if (status != null) {
            q.eq(SysUserEntity::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            q.eq(SysUserEntity::getRoleCode, role);
        }
        List<SysUserEntity> all = sysUserMapper.selectList(q);
        List<AdminUserRecord> filtered = new ArrayList<AdminUserRecord>();
        for (SysUserEntity u : all) {
            if (StringUtils.hasText(keyword)) {
                String k = keyword.toLowerCase();
                boolean match = contains(u.getName(), k) || contains(u.getEmpNo(), k)
                        || contains(u.getEmail(), k) || contains(u.getMobile(), k)
                        || contains(u.getDeptName(), k) || contains(u.getDeptCode(), k);
                if (!match) {
                    continue;
                }
            }
            filtered.add(toRecord(u));
        }
        long total = filtered.size();
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        return new PageResult<AdminUserRecord>(total, pageNum, pageSize, filtered.subList(from, to));
    }

    /** 创建用户并写入默认偏好；邮箱不可重复。 */
    @Transactional
    public AdminUserRecord create(Long actorId, AdminUserCreateRequest req) {
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getEmail, req.getEmail())) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮箱已存在");
        }
        String role = StringUtils.hasText(req.getRole()) ? req.getRole() : AdminAuthHelper.ROLE_EMPLOYEE;
        assertRole(role);
        SysUserEntity user = new SysUserEntity();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setMobile(req.getMobile());
        user.setEmpNo(req.getEmpNo());
        user.setDeptName(req.getDeptName());
        user.setDeptCode(req.getDeptCode());
        user.setRoleCode(role);
        user.setStatus(1);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        sysUserMapper.insert(user);
        UserPreferenceEntity pref = new UserPreferenceEntity();
        pref.setUserId(user.getId());
        pref.setThemeMode("system");
        pref.setNotifyKbUpdate(1);
        pref.setNotifyMention(1);
        pref.setDefaultKbScopes("[\"hr\",\"product\"]");
        userPreferenceMapper.insert(pref);
        auditService.write(actorId, "USER_CREATE", "user", String.valueOf(user.getId()), "创建用户 " + user.getEmail());
        return toRecord(user);
    }

    @Transactional
    public AdminUserRecord approve(Long actorId, Long userId) {
        SysUserEntity user = requireUser(userId);
        user.setStatus(1);
        sysUserMapper.updateById(user);
        auditService.write(actorId, "USER_APPROVE", "user", String.valueOf(userId), "通过审核");
        return toRecord(user);
    }

    @Transactional
    public AdminUserRecord reject(Long actorId, Long userId) {
        SysUserEntity user = requireUser(userId);
        user.setStatus(2);
        sysUserMapper.updateById(user);
        auditService.write(actorId, "USER_REJECT", "user", String.valueOf(userId), "拒绝审核并禁用");
        return toRecord(user);
    }

    @Transactional
    public AdminUserRecord disable(Long actorId, Long userId) {
        SysUserEntity user = requireUser(userId);
        ensureNotLastSysAdmin(user, true);
        user.setStatus(2);
        sysUserMapper.updateById(user);
        auditService.write(actorId, "USER_DISABLE", "user", String.valueOf(userId), "禁用用户");
        return toRecord(user);
    }

    @Transactional
    public AdminUserRecord enable(Long actorId, Long userId) {
        SysUserEntity user = requireUser(userId);
        user.setStatus(1);
        sysUserMapper.updateById(user);
        auditService.write(actorId, "USER_ENABLE", "user", String.valueOf(userId), "启用用户");
        return toRecord(user);
    }

    @Transactional
    public AdminUserRecord changeRole(Long actorId, Long userId, String role) {
        assertRole(role);
        SysUserEntity user = requireUser(userId);
        if (AdminAuthHelper.ROLE_SYS_ADMIN.equals(user.getRoleCode())
                && !AdminAuthHelper.ROLE_SYS_ADMIN.equals(role)) {
            ensureNotLastSysAdmin(user, false);
        }
        user.setRoleCode(role);
        sysUserMapper.updateById(user);
        auditService.write(actorId, "ROLE_UPDATE", "user", String.valueOf(userId), "调整角色为 " + role);
        return toRecord(user);
    }

    /**
     * 重置为随机临时密码并返回明文（仅联调；生产应改为邮件/短信下发）。
     */
    @Transactional
    public String resetPassword(Long actorId, Long userId) {
        SysUserEntity user = requireUser(userId);
        String temp = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        user.setPasswordHash(passwordEncoder.encode(temp));
        sysUserMapper.updateById(user);
        auditService.write(actorId, "USER_RESET_PASSWORD", "user", String.valueOf(userId), "重置密码");
        return "已生成临时密码（未发邮件，仅联调返回）：" + temp;
    }

    /** 保证系统中至少保留一名启用中的 SYS_ADMIN。 */
    private void ensureNotLastSysAdmin(SysUserEntity user, boolean disabling) {
        if (!AdminAuthHelper.ROLE_SYS_ADMIN.equals(user.getRoleCode())) {
            return;
        }
        long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getRoleCode, AdminAuthHelper.ROLE_SYS_ADMIN)
                .eq(SysUserEntity::getStatus, 1));
        if (count <= 1) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    disabling ? "不能禁用最后一个系统管理员" : "不能降级最后一个系统管理员");
        }
    }

    private SysUserEntity requireUser(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户不存在");
        }
        return user;
    }

    private void assertRole(String role) {
        if (!ROLES.contains(role)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法角色");
        }
    }

    private boolean contains(String src, String keyword) {
        return src != null && src.toLowerCase().contains(keyword);
    }

    private AdminUserRecord toRecord(SysUserEntity u) {
        return AdminUserRecord.builder()
                .id(String.valueOf(u.getId()))
                .name(u.getName())
                .empNo(u.getEmpNo())
                .email(u.getEmail())
                .mobile(u.getMobile())
                .deptName(u.getDeptName())
                .deptCode(u.getDeptCode())
                .role(u.getRoleCode())
                .status(u.getStatus())
                .createdAt(u.getCreatedAt() == null ? null : u.getCreatedAt().format(FMT))
                .lastLoginAt(u.getLastLoginAt() == null ? null : u.getLastLoginAt().format(FMT))
                .build();
    }
}
