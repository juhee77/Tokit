package com.tokit.domain.user.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.user.dto.MyPageResponse;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @InjectMocks
    private MyPageService myPageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private User testUser;
    private Asset testAsset;
    private Wallet krwWallet;
    private Wallet tokenWallet;
    private Order openOrder;
    private Trade completedTrade;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .name("MyPage Investor")
                .email("mypage@tokit.com")
                .walletAddress("0xMYPAGE_WALLET_ADDRESS_01")
                .kycStatus(true)
                .build();
        setField(testUser, "id", 1L);

        testAsset = Asset.builder()
                .name("Busan Centum STO")
                .symbol("BUSAN-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 10L);

        krwWallet = Wallet.builder()
                .user(testUser)
                .asset(null)
                .balance(new BigDecimal("2000000"))
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(krwWallet, "id", 100L);

        tokenWallet = Wallet.builder()
                .user(testUser)
                .asset(testAsset)
                .balance(new BigDecimal("50"))
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(tokenWallet, "id", 101L);

        openOrder = Order.builder()
                .user(testUser)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        setField(openOrder, "id", 500L);

        completedTrade = Trade.builder()
                .buyOrder(openOrder)
                .sellOrder(openOrder)
                .asset(testAsset)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .tradedAt(LocalDateTime.now())
                .build();
        setField(completedTrade, "id", 999L);
    }

    @Test
    @DisplayName("getMyPageData: 마이페이지 조회 시 프로필, 지갑 목록, 주문 내역, 체결 내역이 일괄 반환된다.")
    void getMyPageData_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(walletRepository.findAllByUserId(1L)).thenReturn(List.of(krwWallet, tokenWallet));
        when(orderRepository.findByUser_Id(1L)).thenReturn(List.of(openOrder));
        when(tradeRepository.findByUserIdOrderByTradedAtDesc(1L)).thenReturn(List.of(completedTrade));

        // When
        MyPageResponse response = myPageService.getMyPageData(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.user().email()).isEqualTo("mypage@tokit.com");
        assertThat(response.wallets()).hasSize(2);
        assertThat(response.orders()).hasSize(1);
        assertThat(response.trades()).hasSize(1);
    }

    @Test
    @DisplayName("getMyPageData: 존재하지 않는 유저 ID 조회 시 BusinessException 예외가 발생한다.")
    void getMyPageData_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> myPageService.getMyPageData(999L))
                .isInstanceOf(BusinessException.class);
    }
}
