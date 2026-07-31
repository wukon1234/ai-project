package com.zhishiyun.kb.common;

import lombok.Getter;

/** 业务错误码定义。 */
@Getter
public enum ErrorCode {
    /** 请求参数不合法 */
    PARAM_INVALID(40001, "参数错误"),
    /** 未登录或 token 无效 */
    UNAUTHORIZED(40101, "未登录"),
    /** 无目标知识库 ACL 或无管理后台权限 */
    FORBIDDEN_LIBRARY(40301, "无权限"),
    /** 一般业务错误 */
    BIZ_ERROR(40002, "业务错误"),
    /** 用户级问答限流 */
    RATE_LIMITED(42901, "提问过于频繁，请稍后再试"),
    /** 未捕获的系统异常 */
    SYSTEM_ERROR(50001, "系统错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
