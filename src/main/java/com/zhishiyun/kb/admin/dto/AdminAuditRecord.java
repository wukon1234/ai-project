package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

/** 管理后台审计日志展示记录。 */
@Data
@Builder
public class AdminAuditRecord {
    private String id;
    private String actor;
    private String actorEmail;
    /** 动作码，如 LOGIN、INGEST_UPLOAD */
    private String action;
    /** 解析后的目标展示名 */
    private String target;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private String createdAt;
    /** 是否知识相关动作（供前端筛选/着色） */
    private Boolean knowledgeRelated;
}
