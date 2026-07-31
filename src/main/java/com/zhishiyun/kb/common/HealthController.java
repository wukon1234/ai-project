package com.zhishiyun.kb.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhishiyun.kb.infra.mysql.entity.KbLibraryEntity;
import com.zhishiyun.kb.infra.mysql.mapper.KbLibraryMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康检查。 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final KbLibraryMapper kbLibraryMapper;

    /** 探测数据库连通性（查知识库表计数）。 */
    @GetMapping("/db")
    public Result<Map<String, Object>> db() {
        long cnt = kbLibraryMapper.selectCount(new LambdaQueryWrapper<KbLibraryEntity>());
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("status", "UP");
        data.put("libraryCount", cnt);
        return Result.ok(data);
    }
}
