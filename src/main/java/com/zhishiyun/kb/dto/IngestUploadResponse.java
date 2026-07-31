package com.zhishiyun.kb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IngestUploadResponse {
    private Long docId;
    private Long taskId;
}
