package com.zhishiyun.kb.history;

import com.zhishiyun.kb.auth.AuthUser;
import com.zhishiyun.kb.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    public Result<?> history(Authentication auth, @RequestParam(value = "keyword", required = false) String keyword) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        return Result.ok(historyService.history(user.getUserId(), keyword));
    }
}
