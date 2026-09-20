package com.tokit.domain.orderbook.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.order.service.OrderService;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import com.tokit.infra.redis.OrderBookDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

/**
 * 증분 호가창 갱신이 원장과 어긋나지 않는지 검증한다.
 *
 * <p>호가창을 매번 다시 집계하지 않고 변화분만 반영하면, 변경 지점을 하나라도 놓쳤을 때
 * 그 오차가 조용히 누적된다. 화면에 잘못된 호가가 보이는 것으로 끝나지 않고, 그 호가를
 * 보고 낸 주문이 잘못된 가격에 체결된다.
 *
 * <p>따라서 특정 시나리오의 기댓값이 아니라 <b>불변식</b>을 검증한다. 어떤 순서로
 * 주문·취소가 일어나도 <i>증분 집계 = 원장 재집계</i>여야 한다.
 */
@SpringBootTest
class OrderBookConsistencyTest {

    private static final BigDecimal PRICE_BASE = BigDecimal.valueOf(10000);

    @Autowired private OrderService orderService;
    @Autowired private OrderBookService orderBookService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;

    @MockitoBean private ContractService contractService;

    private Asset asset;
    private User buyer;

    @BeforeEach
    void setUp() {
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());

        String uid = UUID.randomUUID().toString();
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("OrderBook Test Issuer").bizRegNo("ob-" + uid).build());
        asset = assetRepository.save(Asset.builder()
                .issuer(issuer).name("OrderBook Test Asset").symbol("OB-" + uid.substring(0, 5))
                .totalSupply(BigDecimal.valueOf(100000)).issuePrice(PRICE_BASE)
                .status("거래중").contractAddress("0xOrderBookTest").build());

        buyer = newUser("obbuyer" + uid.substring(0, 8));
        walletRepository.save(krwWallet(buyer, BigDecimal.valueOf(100_000_000)));
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("주문 접수만 있어도 증분 집계가 원장 재집계와 일치한다.")
    void placementKeepsBookConsistent() {
        for (int i = 0; i < 12; i++) {
            orderService.placeOrder(buyer.getId(), asset.getSymbol(), OrderType.BUY,
                    PRICE_BASE.subtract(BigDecimal.valueOf(i % 4)), BigDecimal.valueOf(2));
        }
        assertBookMatchesLedger();
    }

    @Test
    @DisplayName("주문 취소 후에도 증분 집계가 원장 재집계와 일치한다.")
    void cancellationKeepsBookConsistent() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ids.add(orderService.placeOrder(buyer.getId(), asset.getSymbol(), OrderType.BUY,
                    PRICE_BASE.subtract(BigDecimal.valueOf(i % 3)), BigDecimal.valueOf(3)).getId());
        }
        // 일부만 취소해 같은 가격대에 다른 주문이 남는 상황을 만든다.
        orderService.cancelOrder(ids.get(0), buyer.getId());
        orderService.cancelOrder(ids.get(3), buyer.getId());
        orderService.cancelOrder(ids.get(7), buyer.getId());

        assertBookMatchesLedger();
    }

    @Test
    @DisplayName("같은 가격대의 주문을 모두 취소하면 그 호가는 호가창에서 사라진다.")
    void emptyPriceLevelDisappears() {
        Order first = orderService.placeOrder(buyer.getId(), asset.getSymbol(), OrderType.BUY,
                PRICE_BASE, BigDecimal.valueOf(5));
        Order second = orderService.placeOrder(buyer.getId(), asset.getSymbol(), OrderType.BUY,
                PRICE_BASE, BigDecimal.valueOf(5));

        orderService.cancelOrder(first.getId(), buyer.getId());
        orderService.cancelOrder(second.getId(), buyer.getId());

        OrderBookDto book = orderBookService.getOrderBook(asset.getSymbol(), 20);
        assertThat(book.bids())
                .as("잔량이 0이 된 가격대는 호가로 노출되면 안 된다")
                .noneMatch(e -> e.price().compareTo(PRICE_BASE) == 0);
        assertBookMatchesLedger();
    }

    /** 증분 집계와, 원장(orders)으로부터의 재집계가 같은지 비교한다. */
    private void assertBookMatchesLedger() {
        OrderBookDto incremental = orderBookService.getOrderBook(asset.getSymbol(), 50);

        orderBookService.rebuild(asset.getId());
        OrderBookDto rebuilt = orderBookService.getOrderBook(asset.getSymbol(), 50);

        assertThat(normalize(incremental.bids()))
                .as("매수 호가: 증분 집계가 원장 재집계와 달라졌다")
                .isEqualTo(normalize(rebuilt.bids()));
        assertThat(normalize(incremental.asks()))
                .as("매도 호가: 증분 집계가 원장 재집계와 달라졌다")
                .isEqualTo(normalize(rebuilt.asks()));
    }

    /** BigDecimal은 scale이 다르면 equals가 false이므로 비교 전에 맞춘다. */
    private List<String> normalize(List<OrderBookDto.OrderBookEntry> entries) {
        return entries.stream()
                .map(e -> e.price().stripTrailingZeros().toPlainString()
                        + "@" + e.quantity().stripTrailingZeros().toPlainString())
                .toList();
    }

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .name(tag).email(tag + "@test.com").password("{noop}test-password")
                .walletAddress("0x" + tag).kycStatus(true).build());
    }

    private Wallet krwWallet(User user, BigDecimal balance) {
        return Wallet.builder().user(user).asset(null)
                .balance(balance).lockedBalance(BigDecimal.ZERO).build();
    }
}
