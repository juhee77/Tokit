package com.tokit.global.exception;

import com.tokit.global.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.file.AccessDeniedException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * javax.validation.Valid 또는 @Validated 바인딩 에러가 발생할 때
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("handleMethodArgumentNotValidException", e);
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getStatus().value(), message);
        return new ResponseEntity<>(response, ErrorCode.INVALID_INPUT_VALUE.getStatus());
    }

    /**
     * @ModelAttribute 바인딩 에러가 발생할 때
     */
    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ApiResponse<Object>> handleBindException(BindException e) {
        log.error("handleBindException", e);
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getStatus().value(), message);
        return new ResponseEntity<>(response, ErrorCode.INVALID_INPUT_VALUE.getStatus());
    }

    /**
     * enum type 일치하지 않아 binding 못할 경우 발생
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("handleMethodArgumentTypeMismatchException", e);
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.INVALID_TYPE_VALUE.getStatus().value(), e.getMessage());
        return new ResponseEntity<>(response, ErrorCode.INVALID_TYPE_VALUE.getStatus());
    }

    /**
     * 지원하지 않은 HTTP method 호출 할 경우 발생
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("handleHttpRequestMethodNotSupportedException", e);
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.getStatus().value(), e.getMessage());
        return new ResponseEntity<>(response, ErrorCode.METHOD_NOT_ALLOWED.getStatus());
    }

    /**
     * Authentication 객체가 필요한 권한을 보유하지 않았을 때 발생
     */
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException e) {
        log.error("handleAccessDeniedException", e);
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.HANDLE_ACCESS_DENIED.getStatus().value(), e.getMessage());
        return new ResponseEntity<>(response, ErrorCode.HANDLE_ACCESS_DENIED.getStatus());
    }

    /**
     * 비즈니스 로직 상의 에러가 발생할 때
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Object>> handleBusinessException(final BusinessException e) {
        log.error("handleBusinessException", e);
        final ErrorCode errorCode = e.getErrorCode();
        final ApiResponse<Object> response = ApiResponse.error(errorCode.getStatus().value(), e.getMessage());
        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    /**
     * 그 외 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("handleException", e);
        ApiResponse<Object> response = ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getStatus().value(), e.getMessage());
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getStatus());
    }
}
