package com.tokit.global.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.user.entity.Role;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    private static final String KEY = "test-uuid-v4-key";
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(120);
    private static final Duration COMPLETED_TTL = Duration.ofMinutes(10);

    private static final AuthUser ALICE = new AuthUser(1L, "alice@test.com", Role.USER);
    private static final AuthUser BOB = new AuthUser(2L, "bob@test.com", Role.USER);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** setIfAbsent/get/set/delete를 실제 Redis처럼 동작시키는 저장소. */
    private final Map<String, String> store = new ConcurrentHashMap<>();

    private IdempotencyAspect aspect;

    /** 핸들러 시그니처만 빌려 쓰는 가짜 컨트롤러. */
    @SuppressWarnings("unused")
    static class FakeController {
        record AmountRequest(BigDecimal amount) {
        }

        public ResponseEntity<ApiResponse<String>> deposit(
                @RequestHeader("X-Idempotency-Key") String key,
                @AuthenticationPrincipal AuthUser authUser,
                @RequestBody AmountRequest request) {
            return null;
        }

        public ResponseEntity<ApiResponse<String>> withdraw(
                @RequestHeader("X-Idempotency-Key") String key,
                @AuthenticationPrincipal AuthUser authUser,
                @RequestBody AmountRequest request) {
            return null;
        }

        public ResponseEntity<ApiResponse<Void>> cancel(
                @RequestHeader("X-Idempotency-Key") String key,
                @PathVariable("id") Long id,
                @AuthenticationPrincipal AuthUser authUser) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(redisTemplate, objectMapper, PROCESSING_TTL, COMPLETED_TTL);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        lenient().when(request.getHeader("X-Idempotency-Key")).thenReturn(KEY);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        lenient().when(valueOperations.get(anyString()))
                .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        lenient().doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("X-Idempotency-Key 헤더 누락 시 HTTP 400 Bad Request를 즉시 리턴한다.")
    void missingHeader_Returns400() throws Throwable {
        when(request.getHeader("X-Idempotency-Key")).thenReturn(null);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        ResponseEntity<?> response = (ResponseEntity<?>) aspect.checkIdempotency(joinPoint);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("최초 요청은 실행하고, 같은 사용자·같은 키·같은 본문의 재요청은 저장된 응답을 돌려준다.")
    void sameRequest_ExecutesOnceAndReplays() throws Throwable {
        ProceedingJoinPoint first = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("DONE")));
        ProceedingJoinPoint retry = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("AGAIN")));

        aspect.checkIdempotency(first);
        ResponseEntity<?> replayed = (ResponseEntity<?>) aspect.checkIdempotency(retry);

        verify(first, times(1)).proceed();
        verify(retry, never()).proceed();
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.writeValueAsString(replayed.getBody()))
                .isEqualTo(objectMapper.writeValueAsString(ApiResponse.success("DONE")));
        verify(valueOperations).set(anyString(), anyString(), eq(COMPLETED_TTL));
    }

    @Test
    @DisplayName("처리 중인 같은 요청이 다시 오면 HTTP 409 Conflict를 반환한다.")
    void processing_Returns409() throws Throwable {
        ProceedingJoinPoint first = deposit(ALICE, "50000", null);
        // 첫 요청이 처리되는 도중에 같은 요청이 들어온다.
        ProceedingJoinPoint concurrent = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("X")));
        when(first.proceed()).thenAnswer(inv -> {
            ResponseEntity<?> inFlight = (ResponseEntity<?>) aspect.checkIdempotency(concurrent);
            assertThat(inFlight.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return ResponseEntity.ok(ApiResponse.success("DONE"));
        });

        aspect.checkIdempotency(first);

        verify(concurrent, never()).proceed();
    }

    @Test
    @DisplayName("다른 사용자가 같은 키를 보내도 남의 응답을 돌려주지 않고 각자 실행한다.")
    void differentUser_SameKey_DoesNotLeak() throws Throwable {
        ProceedingJoinPoint alice = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("alice-result")));
        ProceedingJoinPoint bob = deposit(BOB, "50000", ResponseEntity.ok(ApiResponse.success("bob-result")));

        aspect.checkIdempotency(alice);
        ResponseEntity<?> bobResponse = (ResponseEntity<?>) aspect.checkIdempotency(bob);

        verify(bob, times(1)).proceed();
        assertThat(objectMapper.writeValueAsString(bobResponse.getBody())).contains("bob-result");
    }

    @Test
    @DisplayName("같은 키를 다른 API에 쓰면 이전 API의 응답을 돌려주지 않고 실행한다.")
    void differentHandler_SameKey_Executes() throws Throwable {
        ProceedingJoinPoint deposit = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("deposit")));
        ProceedingJoinPoint withdraw = joinPoint("withdraw",
                new Object[]{KEY, ALICE, new FakeController.AmountRequest(new BigDecimal("50000"))},
                ResponseEntity.ok(ApiResponse.success("withdraw")));

        aspect.checkIdempotency(deposit);
        aspect.checkIdempotency(withdraw);

        verify(withdraw, times(1)).proceed();
    }

    @Test
    @DisplayName("같은 키로 본문이 다른 요청이 오면 첫 응답을 돌려주지 않고 HTTP 422로 거부한다.")
    void sameKey_DifferentBody_Returns422() throws Throwable {
        ProceedingJoinPoint first = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("DONE")));
        ProceedingJoinPoint tampered = deposit(ALICE, "90000", ResponseEntity.ok(ApiResponse.success("X")));

        aspect.checkIdempotency(first);
        ResponseEntity<?> response = (ResponseEntity<?>) aspect.checkIdempotency(tampered);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        verify(tampered, never()).proceed();
    }

    @Test
    @DisplayName("같은 키로 경로 변수(대상)가 다른 요청도 HTTP 422로 거부한다.")
    void sameKey_DifferentPathVariable_Returns422() throws Throwable {
        ProceedingJoinPoint cancel1 = joinPoint("cancel", new Object[]{KEY, 1L, ALICE},
                ResponseEntity.ok(ApiResponse.success()));
        ProceedingJoinPoint cancel2 = joinPoint("cancel", new Object[]{KEY, 2L, ALICE},
                ResponseEntity.ok(ApiResponse.success()));

        aspect.checkIdempotency(cancel1);
        ResponseEntity<?> response = (ResponseEntity<?>) aspect.checkIdempotency(cancel2);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        verify(cancel2, never()).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직 예외 발생 시 키를 지워 같은 키로 재시도할 수 있다.")
    void onException_DeletesKeyAndAllowsRetry() throws Throwable {
        ProceedingJoinPoint failing = deposit(ALICE, "50000", null);
        when(failing.proceed()).thenThrow(new RuntimeException("DB Connection Fail"));
        ProceedingJoinPoint retry = deposit(ALICE, "50000", ResponseEntity.ok(ApiResponse.success("DONE")));

        assertThatThrownBy(() -> aspect.checkIdempotency(failing))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB Connection Fail");
        aspect.checkIdempotency(retry);

        verify(retry, times(1)).proceed();
    }

    @Test
    @DisplayName("2xx가 아닌 응답은 저장하지 않고 키를 지워 재시도를 허용한다.")
    void non2xxResponse_DeletesKey() throws Throwable {
        ProceedingJoinPoint rejected = deposit(ALICE, "50000",
                ResponseEntity.badRequest().body(ApiResponse.error(400, "bad")));

        aspect.checkIdempotency(rejected);

        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("응답 저장이 실패해도 이미 처리된 결과는 그대로 돌려주고, 키는 PROCESSING으로 남겨 재실행을 막는다.")
    void cacheWriteFailure_ReturnsResultAndKeepsProcessing() throws Throwable {
        ResponseEntity<ApiResponse<String>> ok = ResponseEntity.ok(ApiResponse.success("DONE"));
        ProceedingJoinPoint first = deposit(ALICE, "50000", ok);
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        Object result = aspect.checkIdempotency(first);

        assertThat(result).isSameAs(ok);
        assertThat(store.values()).singleElement().asString().contains("PROCESSING");
    }

    private ProceedingJoinPoint deposit(AuthUser user, String amount, Object result) throws Throwable {
        return joinPoint("deposit",
                new Object[]{KEY, user, new FakeController.AmountRequest(new BigDecimal(amount))}, result);
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object[] args, Object result) throws Throwable {
        Method method = findMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        lenient().when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(joinPoint.getArgs()).thenReturn(args);
        if (result != null) {
            lenient().when(joinPoint.proceed()).thenReturn(result);
        }
        return joinPoint;
    }

    private static Method findMethod(String name) {
        for (Method method : FakeController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException(name);
    }
}
