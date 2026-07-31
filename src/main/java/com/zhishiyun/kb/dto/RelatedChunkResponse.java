package com.zhishiyun.kb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RelatedChunkResponse {
    private Integer page;
    private String title;
    private String summary;
    private String excerpt;
    private Long chunkId;
}
