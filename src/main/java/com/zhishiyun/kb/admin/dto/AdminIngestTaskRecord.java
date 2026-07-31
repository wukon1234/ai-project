package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

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
    private String status;
    private Integer progress;
    private String errorMsg;
    private String createdAt;
    private Integer pages;
    private String summary;
}
