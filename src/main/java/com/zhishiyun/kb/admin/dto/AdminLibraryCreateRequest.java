package com.zhishiyun.kb.admin.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminLibraryCreateRequest {
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,31}$", message = "code 需英文小写开头，仅含小写字母数字下划线短横")
    private String code;
    @NotBlank
    private String name;
    private String description;
    private List<String> tags;
    private Boolean publicRead;
}
