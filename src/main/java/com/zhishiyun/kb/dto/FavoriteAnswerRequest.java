package com.zhishiyun.kb.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteAnswerRequest {
    @NotNull
    private Long messageId;
    private String summary;
    private String topic;
}
