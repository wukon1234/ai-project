package com.zhishiyun.kb.admin.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLibraryRecord {
    private String id;
    private String code;
    private String name;
    private String description;
    private List<String> tags;
    private Integer docCount;
    private String updatedAt;
    private Boolean publicRead;
}
