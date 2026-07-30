package com.zhishiyun.kb.infra.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_citation")
public class ChatCitationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Integer citeIndex;
    private Long docId;
    private String title;
    private Integer pageNo;
    private String libraryName;
    private String libraryCode;
    private String excerpt;
    private LocalDateTime createdAt;
}
