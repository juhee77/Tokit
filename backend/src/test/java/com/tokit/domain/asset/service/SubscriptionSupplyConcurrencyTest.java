package com.tokit.domain.asset.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.asset.repository.SubscriptionRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 공모 청약의 총 발행량 불변식을 동시 요청에서 검증한다. (#30)
 *
 * <p>종목 행 락이 없으면 모든 요청이 같은 잔여 물량을 읽고 통과해 총 발행량을 넘겨 배정된다.
 */
@SpringBootTest
class SubscriptionSupplyConcurrencyTest {

    private static final int INVESTORS = 10;
    private static final BigDecimal TOTAL_SUPPLY = BigDecimal.valueOf(50);       // 50주
    private static final BigDecimal ISSUE_PRICE = BigDecimal.valueOf(10000);
    private static final BigDecimal AMOUNT_PER_INVESTOR = BigDecimal.valueOf(100000); // 1인당 10주

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private IssuerRepository issuerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private ContractService contractService;

    private Asset asset;
    private final List<User> investors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("Supply Cap Issuer")
                .bizRegNo("SUPPLY-" + uniqueId)
                .build());

        asset = assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("Supply Cap Asset")
                .symbol("SUP-" + uniqueId.substring(0, 8))
                .contractAddress("0xSupplyCapContract")
                .totalSupply(TOTAL_SUPPLY)
                .issuePrice(ISSUE_PRICE)
                .status("청약중")
                .build());

        for (int i = 0; i < INVESTORS; i++) {
            User investor = userRepository.save(User.builder()
                    .name("Investor " + i)
                    .email("supply-" + uniqueId + "-" + i + "@test.com")
                    .walletAddress("0xSupplyInvestor" + i)
                    .password("{noop}test-password")
                    .kycStatus(true)
                    .build());
            investors.add(investor);
            walletRepository.save(Wallet.builder()
                    .user(investor)
                    .asset(null)
                    .balance(BigDecimal.valueOf(1000000))
                    .lockedBalance(BigDecimal.ZERO)
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAll(subscriptionRepository.findAll().stream()
                .filter(s -> s.getAsset().getId().equals(asset.getId()))
                .toList());
        walletRepository.deleteAll(walletRepository.findAll().stream()
                .filter(w -> investors.stream().anyMatch(u -> u.getId().equals(w.getUser().getId())))
                .toList());
    }

    @Test
    @DisplayName("10명이 동시에 10주씩 청약해도 총 발행량 50주를 넘겨 배정되지 않는다.")
    void concurrentSubscriptionsNeverExceedTotalSupply() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(INVESTORS);
        CountDownLatch ready = new CountDownLatch(INVESTORS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(INVESTORS);
        AtomicInteger succeeded = new AtomicInteger();

        for (User investor : investors) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    assetService.subscribeAsset(asset.getSymbol(), investor.getId(), AMOUNT_PER_INVESTOR);
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                    // 물량 소진으로 거절된 청약
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        BigDecimal allocated = subscriptionRepository.sumTokenQuantityByAsset(asset.getId());
        assertThat(allocated).isEqualByComparingTo(TOTAL_SUPPLY);
        assertThat(succeeded.get()).isEqualTo(5);
        // 온체인 배정은 커밋된 청약에 대해서만, 커밋 이후에 실행된다.
        verify(contractService, times(5)).handleTransferByPartition(any(), any(), any(), any(), any());
    }
}
