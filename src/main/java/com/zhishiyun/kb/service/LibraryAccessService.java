package com.zhishiyun.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.entity.KbAclEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将会话 scope 与用户 ACL 求交，得到实际可检索知识库集合。 */
@Service
@RequiredArgsConstructor
public class LibraryAccessService {

    private final KbAclMapper kbAclMapper;

    /** scope 为 all/空时返回全部 ACL；否则与请求库求交。 */
    public Set<String> resolveScopes(Long userId, String scope) {
        Set<String> aclScopes = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>()
                        .eq(KbAclEntity::getUserId, userId))
                .stream()
                .map(KbAclEntity::getLibraryCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
}
