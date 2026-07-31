package com.zhishiyun.kb.admin.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLibraryUpdateRequest {
    @NotBlank
    private String name;
    private String description;
    private List<String> tags;
    private Boolean publicRead;
}
