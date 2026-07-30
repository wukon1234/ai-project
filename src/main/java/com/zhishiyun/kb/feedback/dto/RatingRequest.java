package com.zhishiyun.kb.feedback.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RatingRequest {
    @NotNull
    private Long messageId;
    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;
}
