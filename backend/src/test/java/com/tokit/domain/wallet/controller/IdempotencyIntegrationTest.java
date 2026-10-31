package com.tokit.domain.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.GlobalExceptionHandler;
import com.tokit.support.TestAuthPrincipalResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class IdempotencyIntegrationTest {

    private MockMvc mockMvc;

    // 컨텍스트에서 가져온 컨트롤러는 @Idempotent AOP 프록시이므로, standaloneSetup으로 감싸도
    // 멱등성 애스펙트는 그대로 동작합니다. 인증 주체만 테스트용으로 주입합니다.
    @Autowired
    private WalletController walletController;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        String uniqueId = java.util.UUID.randomUUID().toString();
        testUser = userRepository.save(User.builder()
                .name("Idempotent User")
                .email("idempotent-" + uniqueId + "@tokit.com")
                .walletAddress("0xIDEMPOTENT-" + uniqueId.substring(0, 10))
                .password("{noop}test-password")
                .kycStatus(true)
                .build());

        walletRepository.save(Wallet.builder()
                .user(testUser)
                .asset(null)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build());

        mockMvc = MockMvcBuilders.standaloneSetup(walletController)
                .setCustomArgumentResolvers(
                        new TestAuthPrincipalResolver(testUser.getId(), testUser.getEmail()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        walletRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("멱등성: 동일한 Idempotency-Key로 동시에 여러 번 요청해도 단 1번만 성공하고 나머지는 409를 반환한다.")
    void depositKrw_Idempotency() throws Exception {
        // given
        String idempotencyKey = UUID.randomUUID().toString();
        WalletController.WalletAmountRequest request = new WalletController.WalletAmountRequest(new BigDecimal("50000"));
        String jsonRequest = objectMapper.writeValueAsString(request);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    int statusCode = mockMvc.perform(post("/api/wallets/deposit")
                                    .header("X-Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonRequest))
                            .andReturn().getResponse().getStatus();

                    if (statusCode == 200) {
                        successCount.incrementAndGet();
                    } else if (statusCode == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(successCount.get()).isEqualTo(1); // 오직 1개의 요청만 성공해야 함
        
        // 잔고 검증: 50,000원이 정확히 1번만 충전되어야 함
        Wallet wallet = walletRepository.findKrwWalletByUserId(testUser.getId()).orElseThrow();
        assertThat(wallet.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("50000").stripTrailingZeros());
    }

    @Test
    @DisplayName("멱등성: 다른 사용자가 같은 Idempotency-Key를 보내도 남의 응답을 받지 않고 자기 요청이 실행된다.")
    void depositKrw_SameKeyDifferentUser_DoesNotReplayOthersResponse() throws Exception {
        // given: 두 번째 사용자와 그 사용자의 인증 주체로 동작하는 MockMvc
        String uniqueId = UUID.randomUUID().toString();
        User otherUser = userRepository.save(User.builder()
                .name("Other User")
                .email("other-" + uniqueId + "@tokit.com")
                .walletAddress("0xOTHER-" + uniqueId.substring(0, 10))
                .password("{noop}test-password")
                .kycStatus(true)
                .build());
        walletRepository.save(Wallet.builder()
                .user(otherUser)
                .asset(null)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build());
        MockMvc otherMockMvc = MockMvcBuilders.standaloneSetup(walletController)
                .setCustomArgumentResolvers(
                        new TestAuthPrincipalResolver(otherUser.getId(), otherUser.getEmail()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String sharedKey = UUID.randomUUID().toString();

        // when: 같은 키로 각자 충전한다.
        mockMvc.perform(post("/api/wallets/deposit")
                        .header("X-Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WalletController.WalletAmountRequest(new BigDecimal("50000")))))
                .andExpect(status().isOk());
        otherMockMvc.perform(post("/api/wallets/deposit")
                        .header("X-Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WalletController.WalletAmountRequest(new BigDecimal("30000")))))
                .andExpect(status().isOk());

        // then: 두 번째 사용자의 충전도 실제로 반영되어야 한다.
        //       (키 범위가 사용자로 나뉘지 않으면 첫 사용자의 캐시 응답만 받고 충전되지 않는다)
        Wallet mine = walletRepository.findKrwWalletByUserId(testUser.getId()).orElseThrow();
        Wallet others = walletRepository.findKrwWalletByUserId(otherUser.getId()).orElseThrow();
        assertThat(mine.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("50000").stripTrailingZeros());
        assertThat(others.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("30000").stripTrailingZeros());
    }

    @Test
    @DisplayName("멱등성: 같은 Idempotency-Key로 금액이 다른 요청을 보내면 422로 거부하고 충전하지 않는다.")
    void depositKrw_SameKeyDifferentBody_Returns422() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/wallets/deposit")
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WalletController.WalletAmountRequest(new BigDecimal("50000")))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/wallets/deposit")
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WalletController.WalletAmountRequest(new BigDecimal("90000")))))
                .andExpect(status().is(422));

        Wallet wallet = walletRepository.findKrwWalletByUserId(testUser.getId()).orElseThrow();
        assertThat(wallet.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("50000").stripTrailingZeros());
    }
}
