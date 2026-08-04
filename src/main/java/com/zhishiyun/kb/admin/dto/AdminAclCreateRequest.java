package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/** 新增知识库 ACL 规则请求体。 */
@Data
public class AdminAclCreateRequest {
    /** user | dept */
    @NotBlank
    private String subjectType;
    /** 用户 ID 或部门 code */
    @NotBlank
    private String subjectId;
    /** 可选展示名；缺省由服务端根据用户/部门补全 */
    private String subjectLabel;
    /** 固定 READ */
    private String perm = "READ";
}
