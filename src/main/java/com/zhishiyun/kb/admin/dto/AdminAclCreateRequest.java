package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminAclCreateRequest {
    /** user | dept */
    @NotBlank
    private String subjectType;
    @NotBlank
    private String subjectId;
    private String subjectLabel;
    /** 固定 READ */
    private String perm = "READ";
}
