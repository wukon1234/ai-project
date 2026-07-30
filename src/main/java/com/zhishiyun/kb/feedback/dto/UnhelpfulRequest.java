package com.zhishiyun.kb.feedback.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UnhelpfulRequest {
    @NotNull
    private Long messageId;
    @NotBlank
    private String issueType;
    private String comment;
    @NotNull
    private Boolean knowCorrect;
    private String correctAnswer;
}
