package com.zhishiyun.kb.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AskRequest {
    @NotBlank
    private String question;
}
