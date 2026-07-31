package com.zhishiyun.kb.share;

import com.zhishiyun.kb.common.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分享只读 API（会话 / 文档短链访问）。 */
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /** 按 token 只读访问分享会话。 */
    @GetMapping("/sessions/{token}")
    public Result<Map<String, Object>> session(@PathVariable String token) {
        return Result.ok(shareService.readSessionByToken(token));
    }

    /** 按 token 只读访问分享文档元数据。 */
    @GetMapping("/documents/{token}")
    public Result<Map<String, Object>> document(@PathVariable String token) {
        return Result.ok(shareService.readDocumentByToken(token));
    }
}
