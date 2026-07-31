package com.zhishiyun.kb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageSummaryResponse {
    private Integer pageNo;
    private String knowledgeBase;
    private String summary;
    private Boolean cached;
}
