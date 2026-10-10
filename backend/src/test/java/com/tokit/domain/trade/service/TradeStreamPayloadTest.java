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
import com.tokit.domain.trade.dto.TradeResponse;
import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * 스트리밍 전파 대상이 엔티티가 아닌지 검증한다.
 *
 * <p>{@code Trade}는 {@code buyOrder}, {@code sellOrder}, {@code asset}을 지연 로딩으로
 * 들고 있고 {@code Order}는 다시 {@code user}로 이어진다. 엔티티를 그대로 내보내면
 * 직렬화 시점에 이들을 건드려 트랜잭션 밖에서 예외가 나고, 연관 관계를 따라
 * <b>상대 투자자의 정보까지 내려간다.</b>
 *
 * <p>전파 도중 예외가 나면 더 나쁘다. 전파 로직은 전송 실패를 끊긴 구독자로 간주하므로,
 * 직렬화 오류를 연결 종료로 오인해 <b>정상 구독자를 정리해 버린다.</b>
 */
@SpringBootTest
class TradeStreamPayloadTest {

    @Autowired private TradeService tradeService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private TradeFeeRepository tradeFeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;

    @MockitoBean private TradeStreamBroadcaster tradeStreamBroadcaster;
    @MockitoBean private OrderEventPublisher orderEventPublisher;
    @MockitoBean private ContractService contractService;

    private Asset asset;
    private Order buyOrder;
    private Order sellOrder;

    @BeforeEach
    void setUp() {
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());

        String uid = UUID.randomUUID().toString().substring(0, 8);
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("Payload Test Issuer").bizRegNo("pl-" + uid).build());
        asset = assetRepository.save(Asset.builder()
                .issuer(issuer).name("Payload Test Asset").symbol("PL-" + uid)
                .totalSupply(BigDecimal.valueOf(1000)).issuePrice(BigDecimal.valueOf(10000))
                .status("거래중").contractAddress("0xPayloadTest").build());

        User buyer = newUser("plbuyer" + uid);
        User seller = newUser("plseller" + uid);
        walletRepository.save(wallet(buyer, null, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(10_100)));
        walletRepository.save(wallet(seller, null, BigDecimal.ZERO, BigDecimal.ZERO));
        walletRepository.save(wallet(seller, asset, BigDecimal.ZERO, BigDecimal.ONE));

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
        tradeFeeRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("체결 전파 payload는 엔티티가 아니라 DTO다.")
    void broadcastPayloadMustBeDto() {
        transactionTemplate.execute(status ->
                tradeService.saveTrade(buyOrder.getId(), sellOrder.getId(),
                        asset.getSymbol(), BigDecimal.valueOf(10000), BigDecimal.ONE));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(tradeStreamBroadcaster).broadcast(
                eq(asset.getSymbol()), eq("TRADE"), anyString(), payload.capture());

        Object sent = payload.getValue();
        assertSoftly(softly -> {
            softly.assertThat(sent)
                    .as("엔티티를 내보내면 직렬화 시점에 지연 로딩이 일어나고 상대 투자자 정보까지 노출된다")
                    .isNotInstanceOf(Trade.class);
            softly.assertThat(sent)
                    .as("전파 payload는 TradeResponse여야 한다")
                    .isInstanceOf(TradeResponse.class);
        });

        // DTO가 트랜잭션 안에서 만들어졌으므로 지연 로딩이 이미 해소되어 있어야 한다.
        TradeResponse response = (TradeResponse) sent;
        assertSoftly(softly -> {
            softly.assertThat(response.assetSymbol()).isEqualTo(asset.getSymbol());
            softly.assertThat(response.buyOrderId()).isEqualTo(buyOrder.getId());
            softly.assertThat(response.sellOrderId()).isEqualTo(sellOrder.getId());
            softly.assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            softly.assertThat(response.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(response.id()).isNotNull();
        });
    }

    @Test
    @DisplayName("전파 payload를 트랜잭션 밖에서 직렬화해도 지연 로딩 예외가 나지 않는다.")
    void payloadMustBeSerializableOutsideTransaction() throws Exception {
        transactionTemplate.execute(status ->
                tradeService.saveTrade(buyOrder.getId(), sellOrder.getId(),
                        asset.getSymbol(), BigDecimal.valueOf(10000), BigDecimal.ONE));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(tradeStreamBroadcaster).broadcast(anyString(), anyString(), anyString(), payload.capture());

        // SSE 전송은 트랜잭션·영속성 컨텍스트 밖에서 일어난다. 그 시점에 직렬화가
        // 가능해야 하며, 실패하면 전파 로직이 이를 끊긴 구독자로 오인한다.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String json = mapper.writeValueAsString(payload.getValue());

        assertThat(json)
                .as("체결 식별 정보가 담겨야 한다")
                .contains(asset.getSymbol());
        assertThat(json)
                .as("연관 엔티티 그래프가 노출되면 안 된다")
                .doesNotContain("walletAddress", "password", "kycStatus");
    }

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .name(tag).email(tag + "@test.com").password("{noop}test-password")
                .walletAddress("0x" + tag).kycStatus(true).build());
    }

    private Wallet wallet(User user, Asset walletAsset, BigDecimal balance, BigDecimal locked) {
        return Wallet.builder().user(user).asset(walletAsset)
                .balance(balance).lockedBalance(locked).build();
    }
}
