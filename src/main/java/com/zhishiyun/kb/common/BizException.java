package com.zhishiyun.kb.common;

import lombok.Getter;

/** 业务异常，携带 ErrorCode。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    /** 上游 HTTP 状态码（可选，如 LLM 503），供重试判断。 */
    private final Integer httpStatus;

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BizException(ErrorCode errorCode, String message, Integer httpStatus) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = httpStatus;
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }
}
