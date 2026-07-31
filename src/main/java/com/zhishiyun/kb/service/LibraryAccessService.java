package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.entity.KbAclEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 将会话 scope 与用户 ACL（含部门/全员可读）求交。 */
@Service
@RequiredArgsConstructor
public class LibraryAccessService {

    private final KbAclMapper kbAclMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final SysUserMapper sysUserMapper;

    /** scope 为 all/空时返回全部可访问库；否则与请求库求交。 */
    public Set<String> resolveScopes(Long userId, String scope) {
        Set<String> aclScopes = accessibleLibraryCodes(userId);
        if (scope == null || scope.trim().isEmpty() || "all".equals(scope)) {
            return aclScopes;
        }
        Set<String> requested = Arrays.stream(scope.replace("[", "").replace("]", "").replace("\"", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        aclScopes.retainAll(requested);
        return aclScopes;
    }

    /** 用户是否可读指定知识库。 */
    public boolean canRead(Long userId, String libraryCode) {
        if (!StringUtils.hasText(libraryCode)) {
            return false;
        }
        return accessibleLibraryCodes(userId).contains(libraryCode);
    }

    /** 合并：用户 ACL + 部门 ACL + public_read 库；KB_ADMIN/SYS_ADMIN 可读全部库。 */
    public Set<String> accessibleLibraryCodes(Long userId) {
        Set<String> codes = new LinkedHashSet<String>();
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            return codes;
        }
        String role = user.getRoleCode();
        if ("KB_ADMIN".equals(role) || "SYS_ADMIN".equals(role)) {
            List<KbLibraryEntity> all = kbLibraryMapper.selectList(null);
            for (KbLibraryEntity lib : all) {
                codes.add(lib.getCode());
            }
            return codes;
        }

        List<KbAclEntity> userAcls = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>()
                .eq(KbAclEntity::getUserId, userId));
        for (KbAclEntity acl : userAcls) {
            if (StringUtils.hasText(acl.getLibraryCode())) {
                codes.add(acl.getLibraryCode());
            }
        }

        if (StringUtils.hasText(user.getDeptCode())) {
            List<KbAclEntity> deptAcls = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>()
                    .eq(KbAclEntity::getDeptCode, user.getDeptCode())
                    .isNull(KbAclEntity::getUserId));
            for (KbAclEntity acl : deptAcls) {
                if (StringUtils.hasText(acl.getLibraryCode())) {
                    codes.add(acl.getLibraryCode());
                }
            }
        }

        List<KbLibraryEntity> publicLibs = kbLibraryMapper.selectList(new LambdaQueryWrapper<KbLibraryEntity>()
                .eq(KbLibraryEntity::getPublicRead, 1));
        for (KbLibraryEntity lib : publicLibs) {
            codes.add(lib.getCode());
        }
        return codes;
    }
}
