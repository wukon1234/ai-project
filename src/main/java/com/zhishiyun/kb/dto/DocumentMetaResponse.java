package com.zhishiyun.kb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentMetaResponse {
    private Long id;
    private String title;
    private String knowledgeBase;
    private String knowledgeBaseId;
    private String fileType;
    private Integer pages;
    private String summary;
    private String updatedAt;
    private Integer views;
    private Boolean favorited;
    private String category;
}
