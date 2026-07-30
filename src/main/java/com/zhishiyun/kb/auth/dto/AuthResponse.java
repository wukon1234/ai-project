package com.zhishiyun.kb.auth.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String name;
        private String deptName;
        private String empNo;
        private String roleCode;
        private List<String> defaultKbScopes;
    }
}
