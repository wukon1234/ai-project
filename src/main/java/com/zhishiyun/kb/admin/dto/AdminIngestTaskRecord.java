package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

/** 管理后台入库任务展示记录（含关联文档摘要字段）。 */
@Data
@Builder
public class AdminIngestTaskRecord {
    private String id;
    private String docId;
    private String title;
    private String libraryCode;
    private String libraryName;
    private String fileType;
    private String category;
    /** PENDING / RUNNING / SUCCESS / FAILED 等 */
    private String status;
    private Integer progress;
    private String errorMsg;
    private String createdAt;
    private Integer pages;
    private String summary;
}
