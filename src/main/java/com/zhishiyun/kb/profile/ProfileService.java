package com.zhishiyun.kb.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.infra.mysql.entity.KbAclEntity;
import com.zhishiyun.kb.infra.mysql.entity.SysUserEntity;
import com.zhishiyun.kb.infra.mysql.entity.UserPreferenceEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbAclMapper;
import com.zhishiyun.kb.infra.mysql.mapper.SysUserMapper;
import com.zhishiyun.kb.infra.mysql.mapper.UserPreferenceMapper;
import com.zhishiyun.kb.profile.dto.PreferenceRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 个人中心：资料查询与偏好（主题、默认知识库等）。 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SysUserMapper sysUserMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final KbAclMapper kbAclMapper;

    /** 个人资料。 */
    public Map<String, Object> profile(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        UserPreferenceEntity pref = getOrCreatePref(userId);
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("id", user.getId());
        m.put("name", user.getName());
        m.put("deptName", user.getDeptName());
        m.put("empNo", user.getEmpNo());
        m.put("roleCode", user.getRoleCode());
        m.put("preferences", prefMap(pref));
        return m;
    }

    /** 查询偏好设置。 */
    public Map<String, Object> preferences(Long userId) {
        return prefMap(getOrCreatePref(userId));
    }

    /** 更新主题、通知与默认知识库 scope。 */
    public Map<String, Object> updatePreferences(Long userId, PreferenceRequest req) {
        validateScopes(userId, req.getDefaultKbScopes());
        UserPreferenceEntity pref = getOrCreatePref(userId);
        pref.setNotifyKbUpdate(req.getNotifyKbUpdate());
        pref.setNotifyMention(req.getNotifyMention());
        pref.setThemeMode(req.getThemeMode());
        pref.setDefaultKbScopes(toJson(req.getDefaultKbScopes()));
        userPreferenceMapper.updateById(pref);
        return prefMap(pref);
    }

    /** 新建会话时使用的默认知识库 scope。 */
    public List<String> defaultScopes(Long userId) {
        UserPreferenceEntity pref = getOrCreatePref(userId);
        return parseScopes(pref.getDefaultKbScopes());
    }

    private UserPreferenceEntity getOrCreatePref(Long userId) {
        UserPreferenceEntity pref = userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreferenceEntity>()
                .eq(UserPreferenceEntity::getUserId, userId).last("limit 1"));
        if (pref == null) {
            pref = new UserPreferenceEntity();
            pref.setUserId(userId);
            pref.setNotifyKbUpdate(1);
            pref.setNotifyMention(1);
            pref.setThemeMode("system");
            pref.setDefaultKbScopes("[\"hr\",\"product\"]");
            userPreferenceMapper.insert(pref);
        }
        return pref;
    }

    private void validateScopes(Long userId, List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "defaultKbScopes 不能为空");
        if (scopes.contains("all")) throw new BizException(ErrorCode.PARAM_INVALID, "defaultKbScopes 不能包含 all");
        List<String> acl = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>().eq(KbAclEntity::getUserId, userId))
                .stream().map(KbAclEntity::getLibraryCode).collect(Collectors.toList());
        for (String s : scopes) {
            if (!Arrays.asList("product", "hr", "tech", "support").contains(s)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "defaultKbScopes 非法");
            }
            if (!acl.contains(s)) {
                throw new BizException(ErrorCode.FORBIDDEN_LIBRARY);
            }
        }
    }

    private Map<String, Object> prefMap(UserPreferenceEntity pref) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("notifyKbUpdate", pref.getNotifyKbUpdate());
        m.put("notifyMention", pref.getNotifyMention());
        m.put("themeMode", pref.getThemeMode());
        m.put("defaultKbScopes", parseScopes(pref.getDefaultKbScopes()));
        return m;
    }

    private List<String> parseScopes(String raw) {
        if (raw == null || raw.trim().isEmpty()) return java.util.Collections.emptyList();
        return Arrays.stream(raw.replace("[", "").replace("]", "").replace("\"", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private String toJson(List<String> scopes) {
        return "[\"" + String.join("\",\"", scopes) + "\"]";
    }
}
