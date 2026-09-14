package com.tokit.domain.matching.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.fee.repository.TradeFeeRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.order.service.OrderService;
import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.config.RabbitMQConfig;
import com.tokit.infra.blockchain.ContractService;
import org.springframework.amqp.core.AmqpAdmin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

/**
 * 매칭 경로의 경합(race) 재현 테스트.
 *
 * <p>기존 OrderMatchingConcurrencyTest는 주문을 350ms 간격으로 제출하므로 실제 경합이
 * 발생하지 않는다. 또 매칭은 RabbitMQ 컨슈머에서 실행되는데 리스너 동시성 기본값이 1이라,
 * 운영 기본 설정에서는 큐 컨슈머 한 개가 매칭을 암묵적으로 직렬화한다. 즉 지금의 안전성은
 * 배타 락이 아니라 "컨슈머가 하나뿐"이라는 배포 형태에 의존한다.
 *
 * <p>이 테스트는 리스너 동시성만 올려 그 가정을 깬다. 애플리케이션 코드는 그대로다.
 * 수평 확장(컨슈머 증설 또는 인스턴스 다중화) 시 매칭이 안전한지를 묻는 테스트다.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.concurrency=8",
        "spring.rabbitmq.listener.simple.max-concurrency=8",
        "spring.rabbitmq.listener.simple.prefetch=1"
})
// 리스너 동시성을 올린 전용 컨텍스트다. 캐시에 남으면 이후 테스트가 쓰는 durable 큐를
// 이 컨텍스트의 컨슈머 8개가 함께 소비해 버리므로, 클래스 종료 시 반드시 닫는다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchingRaceConditionTest {

    /** 시장에 존재하는 토큰의 총량. 이 수치는 어떤 경우에도 늘어나서는 안 된다. */
    private static final BigDecimal SUPPLY = BigDecimal.valueOf(10);
    private static final BigDecimal PRICE = BigDecimal.valueOf(10000);
    private static final int BUYER_COUNT = 10;

    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private TradeFeeRepository tradeFeeRepository;

    @Autowired private AmqpAdmin amqpAdmin;

    @MockitoBean private ContractService contractService;

    private Asset asset;
    private User seller;
    private final List<User> buyers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());

        String uid = UUID.randomUUID().toString();
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("Race Test Issuer")
                .bizRegNo("race-" + uid)
                .build());

        asset = assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("Race Test Asset")
                .symbol("RACE-" + uid.substring(0, 5))
                .totalSupply(BigDecimal.valueOf(1000000))
                .issuePrice(PRICE)
                .status("거래중")
                .contractAddress("0xRaceTestContract")
                .build());

        // 매도자 한 명이 시장의 전체 공급량을 들고 있다.
        seller = newUser("seller-" + uid);
        walletRepository.save(krwWallet(seller, BigDecimal.ZERO));
        walletRepository.save(assetWallet(seller, SUPPLY));

        // 매수자들은 각자 공급량 전체를 살 수 있는 자금을 가진다. (수요 >> 공급)
        for (int i = 0; i < BUYER_COUNT; i++) {
            User buyer = newUser("buyer-" + i + "-" + uid);
            buyers.add(buyer);
            walletRepository.save(krwWallet(buyer, BigDecimal.valueOf(200000)));
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("경합: 매수 주문이 동시에 쏟아져도 시장에 존재하지 않는 토큰이 체결되어서는 안 된다.")
    void concurrentBuyOrdersMustNotOversell() throws InterruptedException {
        // given: 공급 전량을 내놓은 매도 주문 하나가 호가창에 올라가 있다.
        orderService.placeOrder(seller.getId(), asset.getSymbol(), OrderType.SELL, PRICE, SUPPLY);
        waitUntilSettled();

        // when: 매수자 전원이 같은 순간에 공급 전량을 요구한다. (수요 100 vs 공급 10)
        ExecutorService pool = Executors.newFixedThreadPool(BUYER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BUYER_COUNT);
        ConcurrentLinkedQueue<Exception> failures = new ConcurrentLinkedQueue<>();

        for (User buyer : buyers) {
            pool.submit(() -> {
                try {
                    start.await();   // 전원이 같은 지점에서 동시에 출발한다.
                    orderService.placeOrder(buyer.getId(), asset.getSymbol(), OrderType.BUY, PRICE, SUPPLY);
                } catch (Exception e) {
                    failures.add(e); // 예외를 삼키지 않고 모아서 검증한다.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        waitUntilSettled();

        // then
        assertThat(failures).as("주문 제출 중 예외").isEmpty();

        BigDecimal tradedQuantity = tradeRepository.findAll().stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tokensInWallets = walletRepository.findAll().stream()
                .filter(w -> w.getAsset() != null && w.getAsset().getId().equals(asset.getId()))
                .map(w -> w.getBalance().add(w.getLockedBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal minLocked = walletRepository.findAll().stream()
                .map(Wallet::getLockedBalance)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // 세 불변식이 각각 어떻게 깨지는지 한 번에 보이도록 소프트 어서션으로 모아 검증한다.
        assertSoftly(softly -> {
            // 1. 존재하지 않는 토큰이 체결될 수 없다.
            softly.assertThat(tradedQuantity)
                    .as("체결 총량(%s)은 시장 공급량(%s)을 넘을 수 없다", tradedQuantity, SUPPLY)
                    .isLessThanOrEqualTo(SUPPLY);

            // 2. 토큰 총량 보존: 주인만 바뀔 뿐 총량은 그대로다.
            softly.assertThat(tokensInWallets.compareTo(SUPPLY))
                    .as("지갑 토큰 총량(잔고+홀딩)은 공급량과 같아야 한다. 실제=%s 기대=%s", tokensInWallets, SUPPLY)
                    .isZero();

            // 3. 홀딩 잔고는 음수가 될 수 없다. (같은 주문이 두 번 체결되면 음수로 내려간다)
            softly.assertThat(minLocked.signum())
                    .as("홀딩 잔고가 음수인 지갑이 있다. 최솟값=%s", minLocked)
                    .isNotNegative();
        });
    }

    /** 비동기 매칭이 더 이상 진행되지 않을 때까지(체결 수가 안정될 때까지) 기다린다. */
    private void waitUntilSettled() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        long previous = -1;
        int stableTicks = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            long current = tradeRepository.count();
            stableTicks = (current == previous) ? stableTicks + 1 : 0;
            previous = current;
            if (stableTicks >= 10) {  // 2초간 변화 없으면 정산 완료로 본다.
                return;
            }
        }
    }

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .name(tag)
                .email(tag + "@test.com")
                .password("{noop}test-password")
                .walletAddress("0x" + tag.replace("-", "").substring(0, Math.min(20, tag.length())))
                .kycStatus(true)
                .build());
    }

    private Wallet krwWallet(User user, BigDecimal balance) {
        return Wallet.builder().user(user).asset(null)
                .balance(balance).lockedBalance(BigDecimal.ZERO).build();
    }

    private Wallet assetWallet(User user, BigDecimal balance) {
        return Wallet.builder().user(user).asset(asset)
                .balance(balance).lockedBalance(BigDecimal.ZERO).build();
    }

    private void cleanUp() {
        // 주문 행만 지우고 큐에 메시지를 남기면, 이후 테스트의 컨슈머가 그 메시지를 집어
        // "Order not found"로 실패한다. 행과 메시지를 함께 비운다.
        amqpAdmin.purgeQueue(RabbitMQConfig.ORDER_QUEUE_NAME, true);
        amqpAdmin.purgeQueue(RabbitMQConfig.TRADE_QUEUE_NAME, true);

        tradeFeeRepository.deleteAll();   // trades를 참조하므로 먼저 지운다.
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }
}
