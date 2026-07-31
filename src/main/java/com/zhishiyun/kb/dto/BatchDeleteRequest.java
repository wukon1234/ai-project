package com.zhishiyun.kb.dto;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class BatchDeleteRequest {
    @NotEmpty
    private List<Long> ids;
}
