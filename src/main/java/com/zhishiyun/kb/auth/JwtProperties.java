package com.zhishiyun.kb.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** JWT 配置项（密钥、过期时间等）。 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.jwt")
public class JwtProperties {
    /** 签发方标识 */
    private String issuer;
    /** HMAC 签名密钥 */
    private String secret;
    /** access token 有效分钟数 */
    private Integer accessTokenMinutes;
    /** refresh token 默认有效天数 */
    private Integer refreshTokenDays;
    /** rememberMe 时 refresh token 有效天数 */
    private Integer refreshTokenRememberDays;
}
