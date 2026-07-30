package com.zhishiyun.kb.favorite.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteDocumentRequest {
    @NotNull
    private Long docId;
    private Integer page;
}
