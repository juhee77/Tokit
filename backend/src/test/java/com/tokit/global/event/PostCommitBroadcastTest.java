package com.tokit.global.event;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.fee.repository.TradeFeeRepository;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import com.tokit.infra.rabbitmq.OrderEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 외부로 내보내는 사건이 커밋 이후에만 일어나는지 검증한다.
 *
 * <p>체결 이벤트는 RabbitMQ를 거쳐 온체인 이체를 트리거한다. 트랜잭션 안에서 발행하면
 * 롤백이 나도 이미 발행된 뒤이므로, <b>원장에 없는 체결이 블록체인에 기록된다.</b>
 * 오프체인 원장은 되돌릴 수 있어도 온체인은 되돌릴 수 없다.
 */
@SpringBootTest
class PostCommitBroadcastTest {

    @Autowired private TradeService tradeService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private TradeFeeRepository tradeFeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;

    @MockitoBean private OrderEventPublisher orderEventPublisher;
    @MockitoBean private ContractService contractService;

    private Asset asset;
    private Order buyOrder;
    private Order sellOrder;

    @BeforeEach
    void setUp() {
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());

        String uid = UUID.randomUUID().toString();
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("PostCommit Issuer").bizRegNo("pc-" + uid).build());
        asset = assetRepository.save(Asset.builder()
                .issuer(issuer).name("PostCommit Asset").symbol("PC-" + uid.substring(0, 5))
                .totalSupply(BigDecimal.valueOf(10000)).issuePrice(BigDecimal.valueOf(10000))
                .status("거래중").contractAddress("0xPostCommit").build());

        User buyer = newUser("pcbuyer" + uid.substring(0, 8));
        User seller = newUser("pcseller" + uid.substring(0, 8));
        walletRepository.save(krwWallet(buyer, BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(10_100)));
        walletRepository.save(krwWallet(seller, BigDecimal.ZERO, BigDecimal.ZERO));
        walletRepository.save(assetWallet(seller, BigDecimal.ZERO, BigDecimal.ONE));

        buyOrder = orderRepository.save(Order.builder()
                .user(buyer).asset(asset).type(OrderType.BUY)
                .price(BigDecimal.valueOf(10000)).quantity(BigDecimal.ONE)
                .remainQty(BigDecimal.ONE).status(OrderStatus.OPEN).build());
        sellOrder = orderRepository.save(Order.builder()
                .user(seller).asset(asset).type(OrderType.SELL)
                .price(BigDecimal.valueOf(10000)).quantity(BigDecimal.ONE)
                .remainQty(BigDecimal.ONE).status(OrderStatus.OPEN).build());
    }

    @AfterEach
    void tearDown() {
        tradeFeeRepository.deleteAll();   // trades를 참조하므로 먼저 지운다.
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 체결 이벤트가 온체인으로 발행되지 않는다.")
    void rollbackMustNotPublishOnChainEvent() {
        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    tradeService.saveTrade(buyOrder.getId(), sellOrder.getId(),
                            asset.getSymbol(), BigDecimal.valueOf(10000), BigDecimal.ONE);
                    // 체결 직후 실패하는 상황. 원장은 롤백되지만, 트랜잭션 안에서 발행했다면
                    // 이벤트는 이미 나간 뒤다.
                    throw new IllegalStateException("체결 직후 장애 재현");
                }))
                .isInstanceOf(IllegalStateException.class);

        verify(orderEventPublisher, never()).publishTrade(any());
    }

    @Test
    @DisplayName("트랜잭션이 커밋되면 체결 이벤트가 정확히 한 번 발행된다.")
    void commitPublishesOnChainEventExactlyOnce() {
        transactionTemplate.execute(status ->
                tradeService.saveTrade(buyOrder.getId(), sellOrder.getId(),
                        asset.getSymbol(), BigDecimal.valueOf(10000), BigDecimal.ONE));

        verify(orderEventPublisher, times(1)).publishTrade(any());
    }

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .name(tag).email(tag + "@test.com").password("{noop}test-password")
                .walletAddress("0x" + tag).kycStatus(true).build());
    }

    private Wallet krwWallet(User user, BigDecimal balance, BigDecimal locked) {
        return Wallet.builder().user(user).asset(null).balance(balance).lockedBalance(locked).build();
    }

    private Wallet assetWallet(User user, BigDecimal balance, BigDecimal locked) {
        return Wallet.builder().user(user).asset(asset).balance(balance).lockedBalance(locked).build();
    }
}
