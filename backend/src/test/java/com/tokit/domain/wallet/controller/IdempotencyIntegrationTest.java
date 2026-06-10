package com.tokit.domain.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
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
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
class IdempotencyIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        walletRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        testUser = userRepository.save(User.builder()
                .name("Idempotent User")
                .email("idempotent@tokit.com")
                .walletAddress("0xIDEMPOTENT")
                .kycStatus(true)
                .build());

        walletRepository.save(Wallet.builder()
                .user(testUser)
                .asset(null)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build());
    }

    @AfterEach
    void tearDown() {
        walletRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("멱등성: 동일한 Idempotency-Key로 동시에 여러 번 요청해도 단 1번만 성공하고 나머지는 409를 반환한다.")
    void depositKrw_Idempotency() throws Exception {
        // given
        String idempotencyKey = UUID.randomUUID().toString();
        WalletController.WalletAmountRequest request = new WalletController.WalletAmountRequest(testUser.getId(), new BigDecimal("50000"));
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
}
