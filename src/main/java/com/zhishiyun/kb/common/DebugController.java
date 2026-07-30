package com.zhishiyun.kb.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/debug")
public class DebugController {

    @GetMapping("/biz-error")
    public Result<Void> bizError() {
        throw new BizException(ErrorCode.PARAM_INVALID, "debug biz error");
    }
}
