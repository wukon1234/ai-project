package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import lombok.Data;

/** 管理后台创建用户请求体。 */
@Data
public class AdminUserCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    @Email
    private String email;
    private String mobile;
    private String empNo;
    private String deptName;
    private String deptCode;
    /** 缺省为 EMPLOYEE */
    private String role;
    @NotBlank
    private String password;
}
