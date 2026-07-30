package com.zhishiyun.kb.profile.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreferenceRequest {
    @NotNull
    private Integer notifyKbUpdate;
    @NotNull
    private Integer notifyMention;
    @NotBlank
    private String themeMode;
    @NotNull
    private List<String> defaultKbScopes;
}
