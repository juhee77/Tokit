package com.tokit.global.dto;

public record ApiResponse<T>(
    int status,
    String message,
    T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "SUCCESS", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "SUCCESS", null);
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}
