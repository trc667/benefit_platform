package com.campus.growth.common.exception;

import com.campus.growth.common.result.ErrorCode;
import com.campus.growth.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>三类异常分开处理：</p>
 * <ol>
 *   <li>业务异常 {@link BizException}：可预期，直接返回业务码，不打错误堆栈；</li>
 *   <li>参数校验异常：返回 400 并带上第一个校验失败原因，方便前端定位；</li>
 *   <li>系统异常：打印完整堆栈 + 请求路径，返回统一兜底文案，避免泄露内部细节。</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletRequest request) {
        // 业务异常属于正常流量的一部分（如重复签到），用 warn 级别并只打必要信息
        log.warn("业务异常 uri={} code={} msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result<Void> handleParam(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "请求参数格式不正确");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不支持：" + e.getMethod());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandler(NoHandlerFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND, "接口不存在：" + e.getRequestURL());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        // 唯一索引兜底：并发场景下重复插入会走到这里，语义上等同于"请勿重复操作"
        log.warn("唯一索引冲突: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.TOO_MANY_REQUESTS, "操作重复，请勿重复提交");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={} method={}", request.getRequestURI(), request.getMethod(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
