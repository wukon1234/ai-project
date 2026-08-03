package com.zhishiyun.kb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageSummaryResponse {
    private Integer pageNo;
    private String knowledgeBase;
    private String summary;
    private Boolean cached;
}
