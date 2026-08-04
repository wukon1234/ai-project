package com.zhishiyun.kb.admin.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 管理后台仪表盘概览响应。 */
@Data
@Builder
public class AdminDashboardResponse {
    private Integer libraryCount;
    private Integer readyDocCount;
    private Integer totalDocCount;
    private Integer failedIngestCount;
    /** SYS_ADMIN 可见；KB_ADMIN 为 null */
    private Integer pendingUserCount;
    private List<AdminIngestTaskRecord> recentIngestTasks;
    private List<AdminAuditRecord> recentAudits;
}
