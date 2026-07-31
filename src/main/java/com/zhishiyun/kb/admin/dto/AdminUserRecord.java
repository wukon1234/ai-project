package com.zhishiyun.kb.admin.dto;

import lombok.Builder;
import lombok.Data;

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
    private String role;
    private Integer status;
    private String createdAt;
    private String lastLoginAt;
}
