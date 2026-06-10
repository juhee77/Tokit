package com.tokit.domain.wallet.service;

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

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Test User")
                .email("test.concurrency@tokit.com")
                .walletAddress("0xCONCURRENCY_TEST")
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
    }

    @Test
    @DisplayName("비관적 락: 100건의 충전 요청이 동시에 발생해도 예치금 잔고가 정확히 합산되어야 한다.")
    void depositKrw_Concurrency() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        BigDecimal depositAmount = new BigDecimal("10000"); // 1만원씩 100번 충전 = 100만원

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    walletService.depositKrw(testUser.getId(), depositAmount);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        Wallet wallet = walletRepository.findKrwWalletByUserId(testUser.getId()).orElseThrow();
        assertThat(wallet.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("1000000").stripTrailingZeros());
    }
}
