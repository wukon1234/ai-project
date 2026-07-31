package com.zhishiyun.kb.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.admin.AdminAuthHelper;
import com.zhishiyun.kb.admin.dto.AdminDashboardResponse;
import com.zhishiyun.kb.common.enums.DocStatus;
import com.zhishiyun.kb.entity.IngestTaskEntity;
import com.zhishiyun.kb.entity.KbDocumentEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.IngestTaskMapper;
import com.zhishiyun.kb.mapper.KbDocumentMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import com.zhishiyun.kb.model.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final KbLibraryMapper kbLibraryMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final IngestTaskMapper ingestTaskMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminIngestService adminIngestService;
    private final AdminAuditQueryService adminAuditQueryService;

    public AdminDashboardResponse overview(AuthUser user) {
        long libraryCount = kbLibraryMapper.selectCount(null);
        long totalDoc = kbDocumentMapper.selectCount(null);
        long readyDoc = kbDocumentMapper.selectCount(new LambdaQueryWrapper<KbDocumentEntity>()
                .eq(KbDocumentEntity::getStatus, DocStatus.READY.name()));
        long failed = ingestTaskMapper.selectCount(new LambdaQueryWrapper<IngestTaskEntity>()
                .eq(IngestTaskEntity::getStatus, "FAILED"));
        Integer pendingUsers = null;
        if (AdminAuthHelper.isSysAdmin(user)) {
            pendingUsers = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                    .eq(SysUserEntity::getStatus, 0)).intValue();
        }
        return AdminDashboardResponse.builder()
                .libraryCount((int) libraryCount)
                .readyDocCount((int) readyDoc)
                .totalDocCount((int) totalDoc)
                .failedIngestCount((int) failed)
                .pendingUserCount(pendingUsers)
                .recentIngestTasks(adminIngestService.recent(8))
                .recentAudits(adminAuditQueryService.recent(user, 8))
                .build();
    }
}
