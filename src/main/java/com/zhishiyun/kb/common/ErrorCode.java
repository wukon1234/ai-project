package com.zhishiyun.kb.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    PARAM_INVALID(40001, "参数错误"),
    UNAUTHORIZED(40101, "未登录"),
    FORBIDDEN_LIBRARY(40301, "无库权限"),
    BIZ_ERROR(40002, "业务错误"),
    /** 用户级问答限流 */
    RATE_LIMITED(42901, "提问过于频繁，请稍后再试"),
    SYSTEM_ERROR(50001, "系统错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
