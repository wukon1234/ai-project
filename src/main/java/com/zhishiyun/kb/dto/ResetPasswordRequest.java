package com.zhishiyun.kb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

/** 重置密码请求。 */
@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;
    @NotBlank
    @Size(min = 6, max = 64)
    private String newPassword;
}
