package com.zhishiyun.kb.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.infra.mysql.entity.KbAclEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbAclMapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LibraryAccessService {

    private final KbAclMapper kbAclMapper;

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
