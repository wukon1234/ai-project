package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAuditRecord {
    private String id;
    private String actor;
    private String actorEmail;
    private String action;
    private String target;
    private String targetType;
    private String targetId;
    private String detail;
    private String ip;
    private String createdAt;
    private Boolean knowledgeRelated;
}
