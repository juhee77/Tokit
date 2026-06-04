package com.tokit.global.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.global.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String HEADER_KEY = "X-Idempotency-Key";
    private static final String PROCESSING = "PROCESSING";

    @Around("@annotation(com.tokit.global.annotation.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader(HEADER_KEY);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Missing X-Idempotency-Key header"));
        }

        String redisKey = "idempotency:" + idempotencyKey;

        // 1. SETNX (Key가 없으면 PROCESSING 저장, 120초 만료)
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING, Duration.ofSeconds(120));

        if (Boolean.FALSE.equals(isSet)) {
            // 이미 키가 존재함
            String value = redisTemplate.opsForValue().get(redisKey);
            if (PROCESSING.equals(value)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "이미 처리 중인 요청입니다."));
            } else {
                // 성공 캐싱 응답 리턴
                return ResponseEntity.ok(objectMapper.readValue(value, ApiResponse.class));
            }
        }

        // 2. 비즈니스 로직 실행
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            // 예외 발생 시 키 삭제 (재시도 허용)
            redisTemplate.delete(redisKey);
            throw e;
        }

        // 3. 성공 시 결과 캐싱
        if (result instanceof ResponseEntity<?> responseEntity) {
            if (responseEntity.getBody() instanceof ApiResponse<?> apiResponse) {
                String jsonResult = objectMapper.writeValueAsString(apiResponse);
                redisTemplate.opsForValue().set(redisKey, jsonResult, Duration.ofMinutes(10));
            }
        }

        return result;
    }
}
