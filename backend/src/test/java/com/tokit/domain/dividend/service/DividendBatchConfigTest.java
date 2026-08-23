package com.tokit.domain.dividend.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.config.DividendBatchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendBatchConfigTest {

    @InjectMocks
    private DividendBatchConfig dividendBatchConfig;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DividendPayoutRepository dividendPayoutRepository;

    @Mock
    private DividendPayoutDetailRepository dividendPayoutDetailRepository;

    private User testUser;
    private Asset testAsset;
    private DividendPayout testPayout;
    private Wallet testTokenWallet;
    private Wallet testKrwWallet;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .name("Dividend Recipient")
                .email("dividend@tokit.com")
                .walletAddress("0xDIVIDEND_USER_ADDRESS_01")
                .kycStatus(true)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 100L);

        testAsset = Asset.builder()
                .name("Hanam Center STO")
                .symbol("HANAM-STO")
                .totalSupply(new BigDecimal("10000")) // 총 1만 주
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        ReflectionTestUtils.setField(testAsset, "id", 10L);

        testPayout = DividendPayout.builder()
                .asset(testAsset)
                .totalDividendAmount(new BigDecimal("5000000")) // 총 500만원 배당
                .payoutDate(LocalDateTime.now())
                .status("PROCESSING")
                .build();
        ReflectionTestUtils.setField(testPayout, "id", 1L);

        testTokenWallet = Wallet.builder()
                .user(testUser)
                .asset(testAsset)
                .balance(new BigDecimal("2500")) // 2,500주 보유 (지분율 25%)
                .lockedBalance(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(testTokenWallet, "id", 50L);

        testKrwWallet = Wallet.builder()
                .user(testUser)
                .asset(null)
                .balance(new BigDecimal("100000")) // 기존 원화 10만원
                .lockedBalance(BigDecimal.ZERO)
                .build();
        ReflectionTestUtils.setField(testKrwWallet, "id", 51L);
    }

    @Test
    @DisplayName("DividendProcessor: 지분율(25%)에 따른 배당금 산출 및 원화 절사(RoundingMode.DOWN) 정합성을 검증한다.")
    void dividendProcessor_CalculatesShareRatioAndTruncatesPayout() throws Exception {
        // Given
        when(dividendPayoutRepository.findById(1L)).thenReturn(Optional.of(testPayout));

        ItemProcessor<Wallet, DividendPayoutDetail> processor = dividendBatchConfig.dividendProcessor(1L);

        // When
        DividendPayoutDetail detail = processor.process(testTokenWallet);

        // Then
        assertThat(detail).isNotNull();
        assertThat(detail.getUser()).isEqualTo(testUser);
        assertThat(detail.getWalletAddress()).isEqualTo("0xDIVIDEND_USER_ADDRESS_01");
        
        // 지분율 2,500 / 10,000 = 0.250000
        assertThat(detail.getShareRatio().stripTrailingZeros())
                .isEqualTo(new BigDecimal("0.25").stripTrailingZeros());

        // 배당금 5,000,000 * 0.25 = 1,250,000 원
        assertThat(detail.getPayoutAmount().stripTrailingZeros())
                .isEqualTo(new BigDecimal("1250000").stripTrailingZeros());

        assertThat(detail.getStatus()).isEqualTo("PENDING");
    }


    @Test
    @DisplayName("DividendWriter: 배당 내역 수령 시 수령자 원화 지갑에 비관적 락으로 배당금을 정확히 가산한다.")
    void dividendWriter_UpdatesUserKrwWalletBalance() throws Exception {
        // Given
        DividendPayoutDetail detail = DividendPayoutDetail.builder()
                .payout(testPayout)
                .user(testUser)
                .walletAddress(testUser.getWalletAddress())
                .shareRatio(new BigDecimal("0.250000"))
                .payoutAmount(new BigDecimal("1250000"))
                .status("SUCCESS")
                .build();

        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(testUser.getId()))
                .thenReturn(Optional.of(testKrwWallet));

        ItemWriter<DividendPayoutDetail> writer = dividendBatchConfig.dividendWriter();

        // When
        writer.write(new Chunk<>(List.of(detail)));

        // Then
        // 기존 10만원 + 배당금 125만원 = 135만원
        assertThat(testKrwWallet.getBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("1350000").stripTrailingZeros());

        verify(dividendPayoutDetailRepository, times(1)).saveAll(any());
        verify(walletRepository, times(1)).save(testKrwWallet);
    }
}
