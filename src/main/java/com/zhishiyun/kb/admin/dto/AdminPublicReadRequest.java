package com.zhishiyun.kb.admin.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminPublicReadRequest {
    @NotNull
    private Boolean publicRead;
}
