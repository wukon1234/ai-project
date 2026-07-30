package com.zhishiyun.kb.ingest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IngestTaskResponse {
    private String status;
    private Integer progress;
    private String errorMsg;
}
