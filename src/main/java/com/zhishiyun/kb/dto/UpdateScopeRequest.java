package com.zhishiyun.kb.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateScopeRequest {
    @NotBlank
    private String scope;
}
