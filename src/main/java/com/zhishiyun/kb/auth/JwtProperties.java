package com.zhishiyun.kb.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kb.jwt")
public class JwtProperties {
    private String issuer;
    private String secret;
    private Integer accessTokenMinutes;
    private Integer refreshTokenDays;
    private Integer refreshTokenRememberDays;
}
