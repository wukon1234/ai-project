package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Refresh Token 哈希存储，对应 sys_refresh_token。 */
@Data
@TableName("sys_refresh_token")
public class SysRefreshTokenEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expireAt;
    private Integer rememberMe;
    private Integer revoked;
    private LocalDateTime createdAt;
}
