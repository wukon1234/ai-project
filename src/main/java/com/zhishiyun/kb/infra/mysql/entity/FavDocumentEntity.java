package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

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
