package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

/** 开关知识库「全员可读」请求体。 */
@Data
public class AdminPublicReadRequest {
    @NotNull
    private Boolean publicRead;
}
