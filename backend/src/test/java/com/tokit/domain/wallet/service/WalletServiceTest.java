package com.tokit.domain.wallet.service;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @InjectMocks
    private WalletService walletService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    private User testUser;
    private Wallet testKrwWallet;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .name("Wallet User")
                .email("wallet@tokit.com")
                .walletAddress("0xWALLET_USER_ADDRESS_01")
                .kycStatus(true)
                .build();
        setField(testUser, "id", 100L);

        testKrwWallet = Wallet.builder()
                .user(testUser)
                .asset(null)
                .balance(new BigDecimal("500000")) // 초기 50만원
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(testKrwWallet, "id", 10L);
    }

    @Test
    @DisplayName("depositKrw: 비관적 락으로 예치금을 정상적으로 충전(50만 + 100만 = 150만 원)한다.")
    void depositKrw_Success() {
        // Given
        BigDecimal depositAmount = new BigDecimal("1000000");
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(100L))
                .thenReturn(Optional.of(testKrwWallet));

        // When
        WalletResponse response = walletService.depositKrw(100L, depositAmount);

        // Then
        assertThat(response).isNotNull();
        assertThat(testKrwWallet.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("1500000").stripTrailingZeros());
    }

    @Test
    @DisplayName("depositKrw: 충전 금액이 0원 이하일 경우 IllegalArgumentException 예외가 발생한다.")
    void depositKrw_InvalidAmount_ThrowsException() {
        assertThatThrownBy(() -> walletService.depositKrw(100L, new BigDecimal("-10000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("충전 금액은 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("withdrawKrw: 비관적 락으로 출금 가능 잔고에서 정상 출금(50만 - 20만 = 30만 원)한다.")
    void withdrawKrw_Success() {
        // Given
        BigDecimal withdrawAmount = new BigDecimal("200000");
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(100L))
                .thenReturn(Optional.of(testKrwWallet));

        // When
        WalletResponse response = walletService.withdrawKrw(100L, withdrawAmount);

        // Then
        assertThat(response).isNotNull();
        assertThat(testKrwWallet.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("300000").stripTrailingZeros());
    }

    @Test
    @DisplayName("withdrawKrw: 출금 가능 잔고(50만 원)보다 많은 금액(100만 원) 출금 시 예외가 발생한다.")
    void withdrawKrw_InsufficientBalance_ThrowsException() {
        // Given
        BigDecimal overAmount = new BigDecimal("1000000");
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(100L))
                .thenReturn(Optional.of(testKrwWallet));

        // When & Then
        assertThatThrownBy(() -> walletService.withdrawKrw(100L, overAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출금 가능 잔고가 부족합니다.");
    }
}
