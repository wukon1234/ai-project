package com.zhishiyun.kb.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 调试接口（仅开发环境使用）。 */
@RestController
@RequestMapping("/api/v1/debug")
public class DebugController {

    /** 主动抛出业务异常，用于联调全局异常处理。 */
    @GetMapping("/biz-error")
    public Result<Void> bizError() {
        throw new BizException(ErrorCode.PARAM_INVALID, "debug biz error");
    }
}
