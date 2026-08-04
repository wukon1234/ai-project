package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/** 调整用户角色请求体。 */
@Data
public class AdminUserRoleRequest {
    /** EMPLOYEE / KB_ADMIN / SYS_ADMIN */
    @NotBlank
    private String role;
}
