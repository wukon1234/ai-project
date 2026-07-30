package com.zhishiyun.kb.favorite.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteAnswerRequest {
    @NotNull
    private Long messageId;
    private String summary;
    private String topic;
}
