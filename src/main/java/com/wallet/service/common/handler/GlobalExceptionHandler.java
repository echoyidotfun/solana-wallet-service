package com.wallet.service.common.handler;

import com.wallet.service.common.dto.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理参数缺失异常
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleMissingParams(MissingServletRequestParameterException ex) {
        return new ApiResponse<>(false, "Parameter: " + ex.getParameterName() + " is required", null);
    }
    
    // 处理参数类型错误
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ApiResponse<>(false, "Invalid argument type: " + ex.getName() + " 应为 " + ex.getRequiredType().getSimpleName(), null);
    }
    
    // 处理业务逻辑异常
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalState(IllegalStateException ex) {
        return new ApiResponse<>(false, ex.getMessage(), null);
    }
    
    // 处理所有其他异常
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleGenericException(Exception ex) {
        return new ApiResponse<>(false, "Internal server error: " + ex.getMessage(), null);
    }
}

