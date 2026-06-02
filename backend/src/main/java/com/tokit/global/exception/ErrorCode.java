package com.tokit.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // Common Exceptions
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", " Invalid input value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", " Method not allowed"),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", " Entity not found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", " Server error"),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C005", " Invalid type value"),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C006", " Access denied"),

    // User Exceptions
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", " User not found"),
    EMAIL_DUPLICATION(HttpStatus.BAD_REQUEST, "M002", " Email already registered"),

    // Asset Exceptions
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", " Security Token not found"),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "A002", " Insufficient token balance"),

    // Order & Matching Exceptions
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", " Order not found"),
    INVALID_ORDER_PRICE(HttpStatus.BAD_REQUEST, "O002", " Invalid order price"),
    INVALID_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "O003", " Invalid order quantity");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(final HttpStatus status, final String code, final String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
