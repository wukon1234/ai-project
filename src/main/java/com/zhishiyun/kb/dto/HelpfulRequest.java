package com.zhishiyun.kb.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HelpfulRequest {
    @NotNull
    private Long messageId;
}
