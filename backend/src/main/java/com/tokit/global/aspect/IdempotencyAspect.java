package com.tokit.global.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * {@code @Idempotent} API의 중복 실행을 막는다.
 *
 * <h3>키 범위</h3>
 * Redis 키는 {@code idempotency:{userId}:{핸들러}:{X-Idempotency-Key}}다. 키를 클라이언트 값만으로
 * 만들면 다른 사용자가 같은 키를 보냈을 때 남의 캐시된 응답이 그대로 반환되고, 다른 API에
 * 같은 키를 재사용해도 이전 응답이 돌아온다. (#20)
 *
 * <h3>요청 지문</h3>
 * 같은 키로 다른 요청(금액·수량·대상)이 오면 첫 응답을 돌려주지 않고 422로 거부한다.
 * 지문은 본문·경로 변수 등 핸들러 인자로 만든다. 인증 주체와 멱등 키 헤더는 키 범위에
 * 이미 들어가므로 지문에서 뺀다.
 *
 * <h3>저장 형태</h3>
 * 값은 {@link IdempotencyRecord} JSON이다. 처리 중에는 {@code PROCESSING}, 2xx 응답이면
 * {@code COMPLETED}로 상태 코드와 본문을 저장해 재요청 시 그대로 돌려준다.
 *
 * <h3>알려진 한계</h3>
 * 처리 시간이 {@code processing-ttl}을 넘으면 키가 만료되어 같은 요청이 다시 실행될 수 있다.
 * Redis 키만으로는 이를 완전히 막을 수 없으며, 최종 방어선은 DB 유니크 제약이어야 한다.
 */
@Aspect
@Component
@Slf4j
public class IdempotencyAspect {

    static final String HEADER_KEY = "X-Idempotency-Key";
    static final String KEY_PREFIX = "idempotency:";
    static final String ANONYMOUS = "anonymous";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration processingTtl;
    private final Duration completedTtl;

    public IdempotencyAspect(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${tokit.idempotency.processing-ttl:120s}") Duration processingTtl,
            @Value("${tokit.idempotency.completed-ttl:10m}") Duration completedTtl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.processingTtl = processingTtl;
        this.completedTtl = completedTtl;
    }

    /** Redis에 저장하는 값. */
    record IdempotencyRecord(State state, String fingerprint, Integer status, String body) {

        enum State { PROCESSING, COMPLETED }

        static IdempotencyRecord processing(String fingerprint) {
            return new IdempotencyRecord(State.PROCESSING, fingerprint, null, null);
        }

        static IdempotencyRecord completed(String fingerprint, int status, String body) {
            return new IdempotencyRecord(State.COMPLETED, fingerprint, status, body);
        }
    }

    @Around("@annotation(com.tokit.global.annotation.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader(HEADER_KEY);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Missing X-Idempotency-Key header");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String redisKey = redisKey(resolveUserId(method, args), method, idempotencyKey);
        String fingerprint = fingerprint(method, args);

        // 1. SETNX: 키가 없을 때만 PROCESSING으로 선점한다.
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, write(IdempotencyRecord.processing(fingerprint)), processingTtl);

        if (Boolean.FALSE.equals(acquired)) {
            return replayOrReject(redisKey, fingerprint);
        }

        // 2. 비즈니스 로직 실행
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // 실패한 요청은 같은 키로 다시 시도할 수 있어야 한다.
            redisTemplate.delete(redisKey);
            throw e;
        }

        // 3. 2xx 응답만 저장한다. 그 밖의 응답은 키를 지워 재시도를 허용한다.
        //    PROCESSING을 남겨 두면 TTL 동안 같은 키의 재시도가 모두 409를 받는다.
        if (result instanceof ResponseEntity<?> response && response.getStatusCode().is2xxSuccessful()) {
            saveCompleted(redisKey, fingerprint, response);
        } else {
            redisTemplate.delete(redisKey);
        }

        return result;
    }

    private Object replayOrReject(String redisKey, String fingerprint) {
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null) {
            // 선점 실패와 조회 사이에 키가 지워졌다(앞선 요청이 실패). 재시도하면 된다.
            return error(HttpStatus.CONFLICT, "이미 처리 중인 요청입니다.");
        }

        IdempotencyRecord record;
        try {
            record = objectMapper.readValue(stored, IdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            // 이전 형식으로 저장된 값이다. 판단할 수 없으므로 실행하지 않고 재시도를 요청한다.
            return error(HttpStatus.CONFLICT, "이미 처리 중인 요청입니다.");
        }

        if (!fingerprint.equals(record.fingerprint())) {
            // 422. Spring 7에서 상수 이름이 바뀌는 중이라 코드값으로 지정한다.
            return error(HttpStatus.valueOf(422),
                    "같은 X-Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
        }

        if (record.state() == IdempotencyRecord.State.PROCESSING) {
            return error(HttpStatus.CONFLICT, "이미 처리 중인 요청입니다.");
        }

        return ResponseEntity.status(record.status()).body(readBody(record.body()));
    }

    private void saveCompleted(String redisKey, String fingerprint, ResponseEntity<?> response) {
        try {
            String body = objectMapper.writeValueAsString(response.getBody());
            redisTemplate.opsForValue().set(redisKey,
                    write(IdempotencyRecord.completed(fingerprint, response.getStatusCode().value(), body)),
                    completedTtl);
        } catch (RuntimeException | JsonProcessingException e) {
            // 비즈니스 로직은 이미 커밋되었다. 여기서 실패를 던지면 클라이언트는 실패로 알고
            // 재시도한다. 키는 PROCESSING으로 남겨 TTL 동안 재실행을 막고 응답은 그대로 돌려준다.
            log.error("멱등성 응답 저장 실패. key={}", redisKey, e);
        }
    }

    /** 인증 주체 인자에서 사용자를 찾는다. 없으면 익명으로 묶는다. */
    static String resolveUserId(Method method, Object[] args) {
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof AuthUser authUser && authUser.id() != null) {
                return String.valueOf(authUser.id());
            }
            if (has(annotations[i], AuthenticationPrincipal.class) && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }
        return ANONYMOUS;
    }

    static String redisKey(String userId, Method method, String idempotencyKey) {
        String handler = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        return KEY_PREFIX + userId + ":" + handler + ":" + idempotencyKey;
    }

    /** 인증 주체와 헤더를 뺀 핸들러 인자의 SHA-256. */
    String fingerprint(Method method, Object[] args) {
        Annotation[][] annotations = method.getParameterAnnotations();
        List<Object> payload = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (has(annotations[i], RequestHeader.class)
                    || has(annotations[i], AuthenticationPrincipal.class)
                    || args[i] instanceof AuthUser
                    || args[i] instanceof HttpServletRequest) {
                continue;
            }
            payload.add(args[i]);
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("멱등성 요청 지문을 만들 수 없습니다.", e);
        }
    }

    private static boolean has(Annotation[] annotations, Class<? extends Annotation> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }

    private String write(IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("멱등성 레코드를 직렬화할 수 없습니다.", e);
        }
    }

    /**
     * 저장된 본문을 일반 Map/List로 되살린다. 원래 타입 정보는 없지만 직렬화 결과는 같다.
     */
    private Object readBody(String body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 멱등성 응답을 읽을 수 없습니다.", e);
        }
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
