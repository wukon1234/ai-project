package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

/** 知识库 ACL 规则展示对象。 */
@Data
@Builder
public class AdminAclRule {
    private String id;
    private String libraryCode;
    /** user | dept */
    private String subjectType;
    private String subjectId;
    private String subjectLabel;
    /** 当前仅 READ */
    private String perm;
    /** 用户授权 / 部门授权 */
    private String source;
}
