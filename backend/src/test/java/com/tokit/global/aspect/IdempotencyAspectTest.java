package com.tokit.global.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.global.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    @InjectMocks
    private IdempotencyAspect idempotencyAspect;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }


    @Test
    @DisplayName("X-Idempotency-Key 헤더 누락 시 HTTP 400 Bad Request를 즉시 리턴한다.")
    void checkIdempotency_MissingHeader_Returns400() throws Throwable {
        // Given
        when(request.getHeader("X-Idempotency-Key")).thenReturn(null);

        // When
        Object response = idempotencyAspect.checkIdempotency(joinPoint);

        // Then
        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) response;
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("최초 요청 시 Redis SETNX lock(PROCESSING) 후 비즈니스 로직을 실행하고 결과를 10분간 캐싱한다.")
    void checkIdempotency_FirstRequest_ExecutesAndCaches() throws Throwable {
        // Given
        String key = "test-uuid-v4-key-111";
        when(request.getHeader("X-Idempotency-Key")).thenReturn(key);
        when(valueOperations.setIfAbsent(eq("idempotency:" + key), eq("PROCESSING"), any(Duration.class))).thenReturn(true);

        ResponseEntity<ApiResponse<String>> successResponse = ResponseEntity.ok(ApiResponse.success("SUCCESS_DATA"));
        when(joinPoint.proceed()).thenReturn(successResponse);

        // When
        Object response = idempotencyAspect.checkIdempotency(joinPoint);

        // Then
        assertThat(response).isEqualTo(successResponse);

        verify(joinPoint, times(1)).proceed();
        verify(valueOperations, times(1)).set(eq("idempotency:" + key), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("동일 키로 처리 중(PROCESSING) 재요청 시 HTTP 409 Conflict를 반환한다.")
    void checkIdempotency_ConcurrentProcessing_Returns409() throws Throwable {
        // Given
        String key = "test-uuid-v4-key-222";
        when(request.getHeader("X-Idempotency-Key")).thenReturn(key);
        when(valueOperations.setIfAbsent(eq("idempotency:" + key), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:" + key)).thenReturn("PROCESSING");

        // When
        Object response = idempotencyAspect.checkIdempotency(joinPoint);

        // Then
        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) response;
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직 예외 발생 시 Redis 키를 삭제하여 재시도를 허용한다.")
    void checkIdempotency_OnException_DeletesRedisKey() throws Throwable {
        // Given
        String key = "test-uuid-v4-key-333";
        when(request.getHeader("X-Idempotency-Key")).thenReturn(key);
        when(valueOperations.setIfAbsent(eq("idempotency:" + key), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB Connection Fail"));

        // When & Then
        assertThatThrownBy(() -> idempotencyAspect.checkIdempotency(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB Connection Fail");

        verify(redisTemplate, times(1)).delete("idempotency:" + key);
    }
}
