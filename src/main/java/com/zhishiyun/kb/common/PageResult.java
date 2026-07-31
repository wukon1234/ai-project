package com.zhishiyun.kb.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 分页结果包装。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;
}
