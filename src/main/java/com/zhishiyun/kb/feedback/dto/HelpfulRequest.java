package com.zhishiyun.kb.feedback.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HelpfulRequest {
    @NotNull
    private Long messageId;
}
