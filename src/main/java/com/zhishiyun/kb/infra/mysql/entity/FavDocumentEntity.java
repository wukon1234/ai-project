package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 文档收藏，对应 fav_document。 */
@Data
@TableName("fav_document")
public class FavDocumentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long docId;
    private Integer pageNo;
    private LocalDateTime savedAt;
}
