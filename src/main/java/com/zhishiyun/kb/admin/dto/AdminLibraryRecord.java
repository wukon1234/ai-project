package com.zhishiyun.kb.admin.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 管理后台知识库列表展示记录。 */
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
    /** true 表示全员可读，无需额外 ACL */
    private Boolean publicRead;
}
