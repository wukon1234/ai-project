package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserRoleRequest {
    @NotBlank
    private String role;
}
