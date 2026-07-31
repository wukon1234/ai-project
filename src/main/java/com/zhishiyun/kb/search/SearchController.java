package com.zhishiyun.kb.search;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 知识搜索 API。 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** 知识搜索（向量 + 元数据过滤）。 */
    @GetMapping("/knowledge")
    public Result<Map<String, Object>> searchKnowledge(
            Authentication auth,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "category", defaultValue = "all") String category,
            @RequestParam(value = "sort", defaultValue = "relevance") String sort,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(searchService.search(user.getUserId(), q, category, sort, page, size));
    }

    /** 热搜词。 */
    @GetMapping("/hot")
    public Result<?> hot() {
        return Result.ok(searchService.hotWords());
    }
}
