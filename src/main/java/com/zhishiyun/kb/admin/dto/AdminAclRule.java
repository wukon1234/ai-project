package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAclRule {
    private String id;
    private String libraryCode;
    private String subjectType;
    private String subjectId;
    private String subjectLabel;
    private String perm;
    private String source;
}
