package com.zhishiyun.kb.browse;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BrowseController {

    private final BrowseService browseService;

    @GetMapping("/libraries")
    public Result<?> libraries(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(browseService.libraries(user.getUserId()));
    }

    @GetMapping("/libraries/{code}/documents")
    public Result<?> libraryDocs(
            Authentication auth,
            @PathVariable String code,
            @RequestParam(value = "category", defaultValue = "all") String category,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(browseService.libraryDocs(user.getUserId(), code, category, q, page, size));
    }
}
