package com.zhishiyun.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户偏好（主题、通知、默认知识库），对应 user_preference。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_preference")
public class UserPreferenceEntity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer notifyKbUpdate;
    private Integer notifyMention;
    private String themeMode;
    private String defaultKbScopes;
}
