package com.zhishiyun.kb.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一异常处理，输出 {code,message,data}。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException ex) {
        log.warn("Biz exception code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(httpStatusOf(ex.getCode()))
                .body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : ErrorCode.PARAM_INVALID.getDefaultMessage();
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : ErrorCode.PARAM_INVALID.getDefaultMessage();
        log.warn("Bind failed: {}", message);
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.PARAM_INVALID.getCode(), ErrorCode.PARAM_INVALID.getDefaultMessage()));
    }

    @ExceptionHandler(java.io.FileNotFoundException.class)
    public ResponseEntity<Result<Void>> handleFileNotFound(java.io.FileNotFoundException ex) {
        log.warn("File not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ErrorCode.BIZ_ERROR.getCode(), "暂无数据"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getDefaultMessage()));
    }

    private HttpStatus httpStatusOf(int code) {
        if (code == ErrorCode.UNAUTHORIZED.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.FORBIDDEN_LIBRARY.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ErrorCode.RATE_LIMITED.getCode()) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code == ErrorCode.SYSTEM_ERROR.getCode()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
