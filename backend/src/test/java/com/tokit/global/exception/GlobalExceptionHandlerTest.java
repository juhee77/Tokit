package com.tokit.global.exception;

import com.tokit.global.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("BusinessException 핸들링: ErrorCode에 설정된 HTTP 상태 코드와 예외 메시지로 ApiResponse를 포맷팅한다.")
    void handleBusinessException_ReturnsFormattedResponse() {
        // Given
        BusinessException ex = new BusinessException("회원을 찾을 수 없습니다.", ErrorCode.MEMBER_NOT_FOUND);

        // When
        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleBusinessException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("회원을 찾을 수 없습니다.");

    }

    @Test
    @DisplayName("일반 Exception 핸들링: 예기치 못한 서버 에러 발생 시 HTTP 500 Internal Server Error로 래핑한다.")
    void handleException_ReturnsInternalServerError() {
        // Given
        RuntimeException ex = new RuntimeException("DB Connection Timeout Error");

        // When
        ResponseEntity<ApiResponse<Object>> response = globalExceptionHandler.handleException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("DB Connection Timeout Error");
    }
}

