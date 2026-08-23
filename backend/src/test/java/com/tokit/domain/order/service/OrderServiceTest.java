package com.tokit.domain.order.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.service.AssetService;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.service.UserService;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.rabbitmq.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserService userService;

    @Mock
    private AssetService assetService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private User buyer;
    private User seller;
    private Asset testAsset;
    private Wallet krwWallet;
    private Wallet assetWallet;

    @BeforeEach
    void setUp() {
        buyer = User.builder()
                .name("Order Buyer")
                .email("buyer.order@tokit.com")
                .walletAddress("0xBUYER_ORDER_ADDRESS_01")
                .build();
        ReflectionTestUtils.setField(buyer, "id", 1L);

        seller = User.builder()
                .name("Order Seller")
                .email("seller.order@tokit.com")
                .walletAddress("0xSELLER_ORDER_ADDRESS_02")
                .build();
        ReflectionTestUtils.setField(seller, "id", 2L);

        testAsset = Asset.builder()
                .name("Yeouido STO")
                .symbol("YEOUIDO-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        ReflectionTestUtils.setField(testAsset, "id", 10L);

        krwWallet = Wallet.builder()
                .user(buyer)
                .asset(null)
                .balance(new BigDecimal("1000000")) // 기존 가용 100만원
                .lockedBalance(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(krwWallet, "id", 100L);

        assetWallet = Wallet.builder()
                .user(seller)
                .asset(testAsset)
                .balance(new BigDecimal("50")) // 기존 가용 50주
                .lockedBalance(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(assetWallet, "id", 101L);
    }

    @Test
    @DisplayName("placeOrder (BUY): 10,000원에 5주(총 5만원) 매수 주문 시 KRW 지갑 잔액이 lockedBalance로 이동하고 RabbitMQ 이벤트가 발행된다.")
    void placeOrder_BuyOrder_Success() {
        // Given
        BigDecimal price = new BigDecimal("10000");
        BigDecimal quantity = new BigDecimal("5"); // 총 50,000원

        when(userService.getUserById(1L)).thenReturn(buyer);
        when(assetService.getAssetBySymbol("YEOUIDO-STO")).thenReturn(testAsset);
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(1L)).thenReturn(Optional.of(krwWallet));

        // When
        Order result = orderService.placeOrder(1L, "YEOUIDO-STO", OrderType.BUY, price, quantity);

        // Then
        // 1. KRW 가용 잔액 100만 -> 95만, 락 잔액 0 -> 5만
        assertThat(krwWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("950000").stripTrailingZeros());
        assertThat(krwWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("50000").stripTrailingZeros());

        // 2. 주문 엔티티 영속화 및 RabbitMQ 이벤트 송출 검증
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderEventPublisher, times(1)).publishOrder(any());
    }

    @Test
    @DisplayName("placeOrder (BUY): 예치금 잔액(100만 원)보다 큰 금액(200만 원) 매수 시 예외가 발생한다.")
    void placeOrder_BuyOrder_InsufficientBalance_ThrowsException() {
        // Given
        BigDecimal price = new BigDecimal("20000");
        BigDecimal quantity = new BigDecimal("100"); // 총 2,000,000원 필요

        when(userService.getUserById(1L)).thenReturn(buyer);
        when(assetService.getAssetBySymbol("YEOUIDO-STO")).thenReturn(testAsset);
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(1L)).thenReturn(Optional.of(krwWallet));

        // When & Then
        assertThatThrownBy(() -> orderService.placeOrder(1L, "YEOUIDO-STO", OrderType.BUY, price, quantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("매수 주문을 위한 예치금이 부족합니다.");
    }

    @Test
    @DisplayName("placeOrder (SELL): 20주 매도 주문 시 자산 지갑 잔식이 lockedBalance로 이동한다.")
    void placeOrder_SellOrder_Success() {
        // Given
        BigDecimal price = new BigDecimal("10000");
        BigDecimal quantity = new BigDecimal("20");

        when(userService.getUserById(2L)).thenReturn(seller);
        when(assetService.getAssetBySymbol("YEOUIDO-STO")).thenReturn(testAsset);
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(2L, 10L)).thenReturn(Optional.of(assetWallet));

        // When
        Order result = orderService.placeOrder(2L, "YEOUIDO-STO", OrderType.SELL, price, quantity);

        // Then
        // 가용 주식 50 -> 30, 락 주식 0 -> 20
        assertThat(assetWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("30").stripTrailingZeros());
        assertThat(assetWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("20").stripTrailingZeros());

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderEventPublisher, times(1)).publishOrder(any());
    }

    @Test
    @DisplayName("cancelOrder (BUY): 미체결 매수 주문 취소 시 상태가 CANCELED로 전환되고 홀딩된 예치금이 가용 잔고로 반환된다.")
    void cancelOrder_BuyOrder_ReleasesHeldKrw() {
        // Given: 10,000원 * 5주 = 5만원이 이미 홀딩되어 있는 상태를 재현
        krwWallet.updateBalance(new BigDecimal("950000"), new BigDecimal("50000"));

        Order openOrder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(openOrder, "id", 500L);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(openOrder));
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(1L)).thenReturn(Optional.of(krwWallet));

        // When
        orderService.cancelOrder(500L, 1L);

        // Then
        assertThat(openOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(krwWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("1000000").stripTrailingZeros());
        assertThat(krwWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO.stripTrailingZeros());
    }

    @Test
    @DisplayName("cancelOrder (SELL): 부분 체결된 매도 주문 취소 시 미체결 잔량만큼만 자산 지갑 락이 해제된다.")
    void cancelOrder_PartiallyFilledSellOrder_ReleasesRemainingAssetOnly() {
        // Given: 20주 매도 주문 중 12주는 이미 체결되어 8주만 남은 상태 (락은 20주 전체가 걸려있음)
        assetWallet.updateBalance(new BigDecimal("30"), new BigDecimal("20"));

        Order partialOrder = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("20"))
                .remainQty(new BigDecimal("8"))
                .status(OrderStatus.PARTIAL)
                .build();
        ReflectionTestUtils.setField(partialOrder, "id", 501L);

        when(orderRepository.findById(501L)).thenReturn(Optional.of(partialOrder));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(2L, 10L)).thenReturn(Optional.of(assetWallet));

        // When
        orderService.cancelOrder(501L, 2L);

        // Then: 가용 30 -> 38(잔량 8 반환), 락 20 -> 12(체결분 12는 그대로 유지)
        assertThat(partialOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(assetWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("38").stripTrailingZeros());
        assertThat(assetWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("12").stripTrailingZeros());
    }

    @Test
    @DisplayName("cancelOrder: 이미 전량 체결(FILLED)된 주문을 취소하려 하면 예외가 발생한다.")
    void cancelOrder_AlreadyFilledOrder_ThrowsException() {
        // Given
        Order filledOrder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .remainQty(BigDecimal.ZERO)
                .status(OrderStatus.FILLED)
                .build();
        ReflectionTestUtils.setField(filledOrder, "id", 502L);

        when(orderRepository.findById(502L)).thenReturn(Optional.of(filledOrder));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(502L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_ALREADY_CLOSED);

        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("cancelOrder: 주문 소유자가 아닌 다른 사용자가 취소를 요청하면 접근 거부 예외가 발생하고 아무 것도 변경되지 않는다.")
    void cancelOrder_NotOwner_ThrowsAccessDeniedAndLeavesOrderUntouched() {
        // Given: buyer(1L) 소유 주문을 seller(2L)가 취소 시도
        Order othersOrder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(othersOrder, "id", 503L);

        when(orderRepository.findById(503L)).thenReturn(Optional.of(othersOrder));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(503L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HANDLE_ACCESS_DENIED);

        assertThat(othersOrder.getStatus()).isEqualTo(OrderStatus.OPEN);
        verifyNoInteractions(walletRepository);
    }
}
