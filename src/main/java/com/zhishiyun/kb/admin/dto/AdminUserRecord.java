package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

/** 管理后台用户列表/详情展示记录。 */
@Data
@Builder
public class AdminUserRecord {
    private String id;
    private String name;
    private String empNo;
    private String email;
    private String mobile;
    private String deptName;
    private String deptCode;
    /** EMPLOYEE / KB_ADMIN / SYS_ADMIN */
    private String role;
    /** 0 待审 / 1 启用 / 2 禁用 */
    private Integer status;
    private String createdAt;
    private String lastLoginAt;
}
