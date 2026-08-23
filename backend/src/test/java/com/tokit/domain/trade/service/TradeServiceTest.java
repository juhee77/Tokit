package com.tokit.domain.trade.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.rabbitmq.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @InjectMocks
    private TradeService tradeService;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private User buyer;
    private User seller;
    private Asset testAsset;
    private Order buyOrder;
    private Order sellOrder;
    private Wallet buyerKrwWallet;
    private Wallet sellerKrwWallet;
    private Wallet buyerAssetWallet;
    private Wallet sellerAssetWallet;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        buyer = User.builder()
                .name("Trade Buyer")
                .email("buyer.trade@tokit.com")
                .walletAddress("0xTRADE_BUYER_ADDRESS_01")
                .build();
        setField(buyer, "id", 1L);

        seller = User.builder()
                .name("Trade Seller")
                .email("seller.trade@tokit.com")
                .walletAddress("0xTRADE_SELLER_ADDRESS_02")
                .build();
        setField(seller, "id", 2L);

        testAsset = Asset.builder()
                .name("Gwanghwamun Building STO")
                .symbol("GWANGHWAMUN-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 10L);

        buyOrder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();
        setField(buyOrder, "id", 100L);

        sellOrder = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();
        setField(sellOrder, "id", 200L);

        buyerKrwWallet = Wallet.builder()
                .user(buyer)
                .asset(null)
                .balance(new BigDecimal("500000")) // 가용 50만 원
                .lockedBalance(new BigDecimal("50000")) // 매수 주문으로 5만 원 락
                .build();
        setField(buyerKrwWallet, "id", 1000L);

        sellerKrwWallet = Wallet.builder()
                .user(seller)
                .asset(null)
                .balance(new BigDecimal("100000")) // 가용 10만 원
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(sellerKrwWallet, "id", 1001L);

        buyerAssetWallet = Wallet.builder()
                .user(buyer)
                .asset(testAsset)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(buyerAssetWallet, "id", 2000L);

        sellerAssetWallet = Wallet.builder()
                .user(seller)
                .asset(testAsset)
                .balance(new BigDecimal("10")) // 가용 10주
                .lockedBalance(new BigDecimal("5")) // 매도 주문으로 5주 락
                .build();
        setField(sellerAssetWallet, "id", 2001L);
    }

    @Test
    @DisplayName("saveTrade: 10,000원에 5주(5만 원) 체결 시 매수자/매도자 원화 및 토큰 잔액이 동시 정산된다.")
    void saveTrade_SettlesKrwAndTokens() throws Exception {
        // Given
        BigDecimal tradePrice = new BigDecimal("10000");
        BigDecimal tradeQty = new BigDecimal("5"); // 총 5만 원

        Trade mockTrade = Trade.builder()
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .asset(testAsset)
                .price(tradePrice)
                .quantity(tradeQty)
                .tradedAt(LocalDateTime.now())
                .build();
        setField(mockTrade, "id", 999L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(buyOrder));
        when(orderRepository.findById(200L)).thenReturn(Optional.of(sellOrder));
        when(assetRepository.findBySymbol("GWANGHWAMUN-STO")).thenReturn(Optional.of(testAsset));
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(1L)).thenReturn(Optional.of(buyerKrwWallet));
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(2L)).thenReturn(Optional.of(sellerKrwWallet));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(1L, 10L)).thenReturn(Optional.of(buyerAssetWallet));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(2L, 10L)).thenReturn(Optional.of(sellerAssetWallet));
        when(tradeRepository.save(any(Trade.class))).thenReturn(mockTrade);

        // When
        Trade result = tradeService.saveTrade(100L, 200L, "GWANGHWAMUN-STO", tradePrice, tradeQty);

        // Then
        // 1. 매수자: 홀딩 원화 차감 (5만 -> 0원)
        assertThat(buyerKrwWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO);
        // 2. 매수자: 토큰 수량 증가 (0 -> 5주)
        assertThat(buyerAssetWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("5").stripTrailingZeros());

        // 3. 매도자: 원화 잔액 증가 (10만 -> 15만 원)
        assertThat(sellerKrwWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("150000").stripTrailingZeros());
        // 4. 매도자: 홀딩 토큰 차감 (5 -> 0주)
        assertThat(sellerAssetWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO);

        verify(tradeRepository, times(1)).save(any(Trade.class));
        verify(orderEventPublisher, times(1)).publishTrade(any());
    }

    @Test
    @DisplayName("subscribeTrades: 클라이언트 체결 내역 SSE 스트림 구독 요청 시 SseEmitter를 생성하여 반환한다.")
    void subscribeTrades_ReturnsSseEmitter() {
        // When
        SseEmitter emitter = tradeService.subscribeTrades("GWANGHWAMUN-STO");

        // Then
        assertThat(emitter).isNotNull();
    }

}
