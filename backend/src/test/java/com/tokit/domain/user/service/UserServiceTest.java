package com.tokit.domain.user.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.infra.blockchain.ContractService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContractService contractService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User testUser;
    private Asset defaultAsset;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .name("New Investor")
                .email("investor.new@tokit.com")
                .walletAddress("0xINVESTOR_WALLET_ADDRESS_99")
                .kycStatus(false)
                .build();
        setField(testUser, "id", 100L);

        defaultAsset = Asset.builder()
                .name("Haeundae STO")
                .symbol("HDYT")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .build();
        setField(defaultAsset, "id", 1L);
    }

    @Test
    @DisplayName("signUp: 신규 가입 시 기본 원화 예치금 지갑(10,000,000 KRW) 및 대표 STO 지갑이 시딩된다.")
    void signUp_Success() {
        // Given
        when(userRepository.findByEmail("investor.new@tokit.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(assetRepository.findBySymbol("HDYT")).thenReturn(Optional.of(defaultAsset));
        when(passwordEncoder.encode("tokit1234")).thenReturn("$2a$10$hashed");

        // When
        User result = userService.signUp("investor.new@tokit.com", "New Investor", "tokit1234", "0xINVESTOR_WALLET_ADDRESS_99");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("investor.new@tokit.com");

        verify(walletRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("getUserById: 존재하는 유저 ID 조회 시 유저 엔티티를 반환한다.")
    void getUserById_Success() {
        // Given
        when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

        // When
        User result = userService.getUserById(100L);

        // Then
        assertThat(result).isEqualTo(testUser);
    }

    @Test
    @DisplayName("getUserById: 존재하지 않는 유저 ID 조회 시 BusinessException 예외가 발생한다.")
    void getUserById_NotFound_ThrowsException() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("updateKycStatus (true): KYC 완료 시 온체인 스마트 컨트랙트 화이트리스트에 지갑 주소가 등록된다.")
    void updateKycStatus_True_AddToWhitelist() {
        // Given
        when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

        // When
        User result = userService.updateKycStatus(100L, true);

        // Then
        assertThat(result.isKycStatus()).isTrue();
        verify(contractService, times(1)).addToWhitelist(eq("0xINVESTOR_WALLET_ADDRESS_99"));
    }

    @Test
    @DisplayName("updateKycStatus (false): KYC 해제 시 온체인 스마트 컨트랙트 화이트리스트에서 지갑 주소가 제거된다.")
    void updateKycStatus_False_RemoveFromWhitelist() {
        // Given
        when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

        // When
        User result = userService.updateKycStatus(100L, false);

        // Then
        assertThat(result.isKycStatus()).isFalse();
        verify(contractService, times(1)).removeFromWhitelist(eq("0xINVESTOR_WALLET_ADDRESS_99"));
    }
}
