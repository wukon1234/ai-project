package com.zhishiyun.kb.auth.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import lombok.Data;

/** 忘记密码请求。 */
@Data
public class ForgotPasswordRequest {
    @NotBlank
    @Email
    private String email;
}
