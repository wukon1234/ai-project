package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.dto.AdminAclCreateRequest;
import com.zhishiyun.kb.admin.dto.AdminAclRule;
import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.KbAclEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.service.AuditService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminAclService {

    private final KbAclMapper kbAclMapper;
    private final KbLibraryMapper kbLibraryMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminLibraryService adminLibraryService;
    private final AuditService auditService;

    public List<AdminAclRule> listByLibrary(String libraryCode) {
        KbLibraryEntity lib = adminLibraryService.requireByCode(libraryCode);
        List<KbAclEntity> rows = kbAclMapper.selectList(new LambdaQueryWrapper<KbAclEntity>()
                .eq(KbAclEntity::getLibraryId, lib.getId())
                .orderByAsc(KbAclEntity::getId));
        List<AdminAclRule> rules = new ArrayList<AdminAclRule>();
        for (KbAclEntity row : rows) {
            rules.add(toRule(row));
        }
        return rules;
    }

    @Transactional
    public AdminAclRule add(Long actorId, String libraryCode, AdminAclCreateRequest req) {
        KbLibraryEntity lib = adminLibraryService.requireByCode(libraryCode);
        String type = req.getSubjectType() == null ? "" : req.getSubjectType().trim().toLowerCase();
        if (!"user".equals(type) && !"dept".equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "subjectType 仅支持 user/dept");
        }
        KbAclEntity entity = new KbAclEntity();
        entity.setLibraryId(lib.getId());
        entity.setLibraryCode(lib.getCode());
        entity.setPerm("READ");
        entity.setCreatedAt(LocalDateTime.now());
        String label = req.getSubjectLabel();
        if ("user".equals(type)) {
            Long userId = parseLong(req.getSubjectId());
            SysUserEntity user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "用户不存在");
            }
            Long dup = kbAclMapper.selectCount(new LambdaQueryWrapper<KbAclEntity>()
                    .eq(KbAclEntity::getLibraryId, lib.getId())
                    .eq(KbAclEntity::getUserId, userId));
            if (dup != null && dup > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该用户已有此库权限");
            }
            entity.setUserId(userId);
            if (!StringUtils.hasText(label)) {
                label = user.getName() + " · " + (user.getEmpNo() == null ? "" : user.getEmpNo());
            }
        } else {
            String deptCode = req.getSubjectId().trim();
            Long dup = kbAclMapper.selectCount(new LambdaQueryWrapper<KbAclEntity>()
                    .eq(KbAclEntity::getLibraryId, lib.getId())
                    .eq(KbAclEntity::getDeptCode, deptCode)
                    .isNull(KbAclEntity::getUserId));
            if (dup != null && dup > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "该部门已有此库权限");
            }
            entity.setDeptCode(deptCode);
            if (!StringUtils.hasText(label)) {
                label = deptCode;
            }
        }
        kbAclMapper.insert(entity);
        auditService.write(actorId, "ACL_UPDATE", "acl", String.valueOf(entity.getId()),
                "新增 ACL " + libraryCode + " " + type + ":" + req.getSubjectId());
        AdminAclRule rule = toRule(entity);
        rule.setSubjectLabel(label);
        return rule;
    }

    @Transactional
    public void remove(Long actorId, Long aclId) {
        KbAclEntity entity = kbAclMapper.selectById(aclId);
        if (entity == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ACL 不存在");
        }
        kbAclMapper.deleteById(aclId);
        auditService.write(actorId, "ACL_UPDATE", "acl", String.valueOf(aclId),
                "移除 ACL " + entity.getLibraryCode());
    }

    @Transactional
    public void setPublicRead(Long actorId, String libraryCode, boolean publicRead) {
        KbLibraryEntity lib = adminLibraryService.requireByCode(libraryCode);
        lib.setPublicRead(publicRead ? 1 : 0);
        kbLibraryMapper.updateById(lib);
        auditService.write(actorId, "ACL_UPDATE", "library", libraryCode,
                (publicRead ? "开启" : "关闭") + "全员可读");
    }

    private AdminAclRule toRule(KbAclEntity row) {
        boolean isUser = row.getUserId() != null;
        String subjectType = isUser ? "user" : "dept";
        String subjectId = isUser ? String.valueOf(row.getUserId()) : row.getDeptCode();
        String label;
        String source;
        if (isUser) {
            SysUserEntity user = sysUserMapper.selectById(row.getUserId());
            label = user == null ? subjectId : user.getName() + " · " + (user.getEmpNo() == null ? "" : user.getEmpNo());
            source = "用户授权";
        } else {
            label = row.getDeptCode();
            source = "部门授权";
        }
        return AdminAclRule.builder()
                .id(String.valueOf(row.getId()))
                .libraryCode(row.getLibraryCode())
                .subjectType(subjectType)
                .subjectId(subjectId)
                .subjectLabel(label)
                .perm(row.getPerm() == null ? "READ" : row.getPerm())
                .source(source)
                .build();
    }

    private Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "subjectId 无效");
        }
    }
}
