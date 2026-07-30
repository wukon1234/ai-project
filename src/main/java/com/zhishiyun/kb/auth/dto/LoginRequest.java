package com.zhishiyun.kb.auth.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "请输入邮箱或手机号")
    private String account;
    @NotBlank(message = "请输入密码")
    private String password;
    private Boolean rememberMe;
}
