package com.campus.growth.common.exception;

import com.campus.growth.common.result.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 * <p>只用于"可预期的业务失败"（如余额不足、重复签到），
 * 系统级异常一律抛出原始异常由全局处理器兜底，避免把技术细节暴露给前端。</p>
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    /** 业务异常不需要堆栈，避免高频异常场景下的性能损耗 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
