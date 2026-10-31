package com.tokit.domain.trade.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.fee.repository.TradeFeeRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.config.RabbitMQConfig;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

/**
 * 체결 정산의 지갑 락 순서 테스트. (#21)
 *
 * <p>같은 종목의 매칭은 종목 행 락으로 직렬화되지만, 서로 다른 종목의 매칭은 병렬로 진행된다.
 * 정산이 지갑 락을 "매수자 → 매도자" 순서로 잡으면 다음 두 체결이 서로를 기다린다.
 *
 * <pre>
 *   종목 X: A가 B에게서 매수 → A.krw 락 → B.krw 대기
 *   종목 Y: B가 A에게서 매수 → B.krw 락 → A.krw 대기
 * </pre>
 *
 * <p>PostgreSQL은 교착을 감지해 한쪽 트랜잭션을 중단시킨다. 운영에서는 리스너 재시도가
 * 이를 덮어 주지만 재시도 예산과 지연을 쓰고, 재시도가 소진되면 주문이 DLQ로 간다.
 *
 * <p>두 체결을 같은 순간에 출발시키는 것을 여러 번 반복해 교착이 한 번도 나지 않는지 본다.
 * 경합 테스트라 수정 전에도 매 실행 반드시 실패하는 것은 아니지만, 반복 횟수를 충분히 두어
 * 수정 전 코드에서는 교착이 관측되도록 했다.
 */
@SpringBootTest
class TradeWalletLockOrderTest {

    private static final int ROUNDS = 100;
    private static final BigDecimal PRICE = BigDecimal.valueOf(100);
    private static final BigDecimal QTY = BigDecimal.ONE;
    /** 모든 회차를 정산하고도 남을 만큼 넉넉하게 홀딩해 둔다. */
    private static final BigDecimal PLENTY = BigDecimal.valueOf(1_000_000);

    @Autowired private TradeService tradeService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private TradeFeeRepository tradeFeeRepository;
    @Autowired private AmqpAdmin amqpAdmin;

    @MockitoBean private ContractService contractService;

    private Asset assetX;
    private Asset assetY;
    private Order xBuyByA;
    private Order xSellByB;
    private Order yBuyByB;
    private Order ySellByA;

    @BeforeEach
    void setUp() {
        cleanUp();
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any(), any());

        String uid = UUID.randomUUID().toString();
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("Lock Order Issuer")
                .bizRegNo("lock-" + uid)
                .build());
        assetX = newAsset(issuer, "LKX-" + uid.substring(0, 5));
        assetY = newAsset(issuer, "LKY-" + uid.substring(0, 5));

        User a = newUser("a-" + uid);
        User b = newUser("b-" + uid);
        for (User u : List.of(a, b)) {
            walletRepository.save(wallet(u, null));
            walletRepository.save(wallet(u, assetX));
            walletRepository.save(wallet(u, assetY));
        }

        // 두 사용자가 두 종목에서 서로 반대 방향으로 거래한다.
        xBuyByA = orderRepository.save(order(a, assetX, OrderType.BUY));
        xSellByB = orderRepository.save(order(b, assetX, OrderType.SELL));
        yBuyByB = orderRepository.save(order(b, assetY, OrderType.BUY));
        ySellByA = orderRepository.save(order(a, assetY, OrderType.SELL));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("교착: 두 사용자가 서로 다른 종목에서 반대 방향으로 동시에 체결되어도 교착이 생겨서는 안 된다.")
    void crossedSettlementsOnDifferentSymbolsMustNotDeadlock() throws InterruptedException {
        ConcurrentLinkedQueue<Exception> failures = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (int round = 0; round < ROUNDS; round++) {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            pool.submit(() -> settle(start, done, failures,
                    xBuyByA.getId(), xSellByB.getId(), assetX.getSymbol()));
            pool.submit(() -> settle(start, done, failures,
                    yBuyByB.getId(), ySellByA.getId(), assetY.getSymbol()));

            start.countDown();   // 두 체결을 같은 순간에 출발시킨다.
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }
        pool.shutdown();

        List<Exception> deadlocks = failures.stream().filter(TradeWalletLockOrderTest::isDeadlock).toList();

        assertThat(deadlocks)
                .as("%d회 중 교착으로 중단된 정산 %d건", ROUNDS, deadlocks.size())
                .isEmpty();
        assertThat(failures).as("정산 중 예외").isEmpty();
        assertThat(tradeRepository.count())
                .as("모든 정산이 기록되어야 한다")
                .isEqualTo(ROUNDS * 2L);
    }

    private void settle(CountDownLatch start, CountDownLatch done, ConcurrentLinkedQueue<Exception> failures,
                        Long buyOrderId, Long sellOrderId, String symbol) {
        try {
            start.await();
            tradeService.saveTrade(buyOrderId, sellOrderId, symbol, PRICE, QTY, BigDecimal.ZERO);
        } catch (Exception e) {
            failures.add(e);
        } finally {
            done.countDown();
        }
    }

    /** PostgreSQL 교착 감지(SQLSTATE 40P01)로 중단된 예외인지 원인 사슬을 따라가며 확인한다. */
    private static boolean isDeadlock(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "40P01".equals(sql.getSQLState())) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains("deadlock detected")) {
                return true;
            }
        }
        return false;
    }

    private Asset newAsset(Issuer issuer, String symbol) {
        return assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("Lock Order Asset " + symbol)
                .symbol(symbol)
                .totalSupply(PLENTY)
                .issuePrice(PRICE)
                .status("거래중")
                .contractAddress("0xLockOrderTestContract")
                .build());
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

    private Wallet wallet(User user, Asset asset) {
        return Wallet.builder().user(user).asset(asset)
                .balance(PLENTY).lockedBalance(PLENTY).build();
    }

    private Order order(User user, Asset asset, OrderType type) {
        BigDecimal quantity = QTY.multiply(BigDecimal.valueOf(ROUNDS));
        return Order.builder()
                .user(user)
                .asset(asset)
                .type(type)
                .price(PRICE)
                .quantity(quantity)
                .remainQty(quantity)
                .status(OrderStatus.OPEN)
                .build();
    }

    private void cleanUp() {
        amqpAdmin.purgeQueue(RabbitMQConfig.ORDER_QUEUE_NAME, true);
        amqpAdmin.purgeQueue(RabbitMQConfig.TRADE_QUEUE_NAME, true);

        tradeFeeRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }
}
