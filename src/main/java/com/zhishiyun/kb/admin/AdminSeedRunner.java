package com.zhishiyun.kb.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.entity.UserPreferenceEntity;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 本地/联调：确保管理后台 Mock 账号存在且密码与前端一致。
 * <ul>
 *   <li>admin@zhishiyun.com / admin123 → SYS_ADMIN</li>
 *   <li>kbadmin@zhishiyun.com / kb123 → KB_ADMIN</li>
 * </ul>
 * 已存在账号会校正角色、状态与密码哈希。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeedRunner implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            upsert("admin@zhishiyun.com", "系统管理员", "A0001", "IT", "信息技术部",
                    AdminAuthHelper.ROLE_SYS_ADMIN, "admin123", "13900000001");
            upsert("kbadmin@zhishiyun.com", "知识管理员", "A0002", "OPS", "知识运营",
                    AdminAuthHelper.ROLE_KB_ADMIN, "kb123", "13900000002");
        } catch (Exception e) {
            log.warn("Admin seed skipped: {}", e.getMessage());
        }
    }

    private void upsert(String email, String name, String empNo, String deptCode, String deptName,
                        String role, String rawPassword, String mobile) {
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getEmail, email)
                .last("limit 1"));
        if (user == null) {
            user = new SysUserEntity();
            user.setEmail(email);
            user.setMobile(mobile);
            user.setEmpNo(empNo);
            user.setName(name);
            user.setDeptCode(deptCode);
            user.setDeptName(deptName);
            user.setRoleCode(role);
            user.setStatus(1);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            sysUserMapper.insert(user);
            UserPreferenceEntity pref = new UserPreferenceEntity();
            pref.setUserId(user.getId());
            pref.setThemeMode("system");
            pref.setNotifyKbUpdate(1);
            pref.setNotifyMention(1);
            pref.setDefaultKbScopes("[\"hr\",\"product\",\"tech\",\"support\"]");
            userPreferenceMapper.insert(pref);
            log.info("Seeded admin user {}", email);
            return;
        }
        boolean dirty = false;
        if (!role.equals(user.getRoleCode())) {
            user.setRoleCode(role);
            dirty = true;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            user.setStatus(1);
            dirty = true;
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            dirty = true;
        }
        if (dirty) {
            sysUserMapper.updateById(user);
            log.info("Updated admin user seed {}", email);
        }
    }
}
