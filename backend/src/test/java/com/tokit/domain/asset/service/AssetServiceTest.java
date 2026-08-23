package com.tokit.domain.asset.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.user.entity.InvestorType;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
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
class AssetServiceTest {

    @InjectMocks
    private AssetService assetService;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ContractService contractService;

    private User kycUser;
    private User unkycUser;
    private Asset subscribingAsset;
    private Wallet krwWallet;
    private Wallet tokenWallet;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        kycUser = User.builder()
                .name("KYC Investor")
                .email("kyc@tokit.com")
                .walletAddress("0xKYC_INVESTOR_ADDRESS_01")
                .kycStatus(true)
                .investorType(InvestorType.GENERAL) // 일반투자자 (한도 1천만원)
                .build();
        setField(kycUser, "id", 1L);

        unkycUser = User.builder()
                .name("Un-KYC User")
                .email("unkyc@tokit.com")
                .walletAddress("0xUNKYC_ADDRESS_02")
                .kycStatus(false)
                .build();
        setField(unkycUser, "id", 2L);

        subscribingAsset = Asset.builder()
                .name("Teheran Building STO")
                .symbol("TEHERAN-STO")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000")) // 주당 1만원
                .status("청약중")
                .build();
        setField(subscribingAsset, "id", 10L);

        krwWallet = Wallet.builder()
                .user(kycUser)
                .asset(null)
                .balance(new BigDecimal("5000000")) // 예치금 500만원
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(krwWallet, "id", 100L);

        tokenWallet = Wallet.builder()
                .user(kycUser)
                .asset(subscribingAsset)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(tokenWallet, "id", 101L);
    }

    @Test
    @DisplayName("subscribeAsset: KYC 완료 사용자가 100만원(100주) 청약 성공 시 예치금 차감, 토큰 지급 및 온체인 전송이 수행된다.")
    void subscribeAsset_Success() {
        // Given
        BigDecimal subscribeAmount = new BigDecimal("1000000"); // 100만원 청약
        when(userRepository.findById(1L)).thenReturn(Optional.of(kycUser));
        when(assetRepository.findBySymbol("TEHERAN-STO")).thenReturn(Optional.of(subscribingAsset));
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(1L)).thenReturn(Optional.of(krwWallet));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(1L, 10L)).thenReturn(Optional.of(tokenWallet));

        // When
        assetService.subscribeAsset("TEHERAN-STO", 1L, subscribeAmount);

        // Then
        // 1. 원화 예치금 500만 -> 400만 원 차감
        assertThat(krwWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("4000000").stripTrailingZeros());

        // 2. 토큰 100주 (100만원 / 1만원) 지급
        assertThat(tokenWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("100").stripTrailingZeros());

        // 3. 온체인 계약 실행
        verify(contractService, times(1)).handleTransferByPartition(
                eq("TEHERAN-STO"),
                eq("DEFAULT"),
                anyString(),
                eq(kycUser.getWalletAddress()),
                eq(new BigDecimal("100.0000"))
        );
    }

    @Test
    @DisplayName("subscribeAsset: KYC 미인증 사용자가 청약 시도 시 BusinessException 예외가 발생한다.")
    void subscribeAsset_KycNotCompleted_ThrowsException() {
        // Given
        when(userRepository.findById(2L)).thenReturn(Optional.of(unkycUser));

        // When & Then
        assertThatThrownBy(() -> assetService.subscribeAsset("TEHERAN-STO", 2L, new BigDecimal("100000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("KYC 신원인증이 완료되지 않은 사용자입니다.");
    }

    @Test
    @DisplayName("subscribeAsset: 일반투자자가 투자 한도(1,000만 원) 초과 청약 시 예외가 발생한다.")
    void subscribeAsset_RetailLimitExceeded_ThrowsException() {
        // Given: 기존 900만원 어치(900주) 보유 중
        tokenWallet.updateBalance(new BigDecimal("900"), BigDecimal.ZERO);
        BigDecimal overAmount = new BigDecimal("2000000"); // 추가 200만원 (총 1,100만원 > 1,000만원 한도 초과)

        when(userRepository.findById(1L)).thenReturn(Optional.of(kycUser));
        when(assetRepository.findBySymbol("TEHERAN-STO")).thenReturn(Optional.of(subscribingAsset));
        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(1L)).thenReturn(Optional.of(krwWallet));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(1L, 10L)).thenReturn(Optional.of(tokenWallet));

        // When & Then
        assertThatThrownBy(() -> assetService.subscribeAsset("TEHERAN-STO", 1L, overAmount))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("투자 한도를 초과하여 청약할 수 없습니다");
    }
}
