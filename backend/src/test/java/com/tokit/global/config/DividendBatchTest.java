package com.tokit.global.config;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendBatchTest {

    private DividendBatchConfig dividendBatchConfig;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DividendPayoutRepository dividendPayoutRepository;

    @Mock
    private DividendPayoutDetailRepository dividendPayoutDetailRepository;

    @BeforeEach
    void setUp() {
        dividendBatchConfig = new DividendBatchConfig(
                walletRepository,
                dividendPayoutRepository,
                dividendPayoutDetailRepository
        );
    }

    @Test
    void dividendProcessor_ShouldCalculateCorrectShareRatioAndPayoutAmount() throws Exception {
        // Given
        Long payoutId = 1L;
        Asset asset = Asset.builder()
                .name("Test STO")
                .symbol("TEST-STO")
                .totalSupply(BigDecimal.valueOf(100000)) // 총 10만주
                .build();

        DividendPayout payout = DividendPayout.builder()
                .asset(asset)
                .totalDividendAmount(BigDecimal.valueOf(10000000)) // 총 1,000만원 배당금
                .status("PENDING")
                .build();

        User user = User.builder()
                .name("Investor A")
                .walletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                .build();

        Wallet wallet = Wallet.builder()
                .user(user)
                .asset(asset)
                .balance(BigDecimal.valueOf(25000)) // 25,000주 보유 (25% 지분)
                .lockedBalance(BigDecimal.ZERO)
                .build();

        when(dividendPayoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        ItemProcessor<Wallet, DividendPayoutDetail> processor = dividendBatchConfig.dividendProcessor(payoutId);

        // When
        DividendPayoutDetail detail = processor.process(wallet);

        // Then
        assertNotNull(detail);
        assertEquals(payout, detail.getPayout());
        assertEquals(user, detail.getUser());
        assertEquals("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", detail.getWalletAddress());
        assertEquals(0, detail.getShareRatio().compareTo(BigDecimal.valueOf(0.25))); // 25% 지분율
        assertEquals(0, detail.getPayoutAmount().compareTo(BigDecimal.valueOf(2500000))); // 250만원 배당금
        assertEquals("PENDING", detail.getStatus());
    }

    @Test
    void dividendWriter_ShouldDepositToKrwWalletAndSaveDetails() throws Exception {
        // Given
        User user = User.builder()
                .name("Investor B")
                .build();

        Wallet krwWallet = Wallet.builder()
                .user(user)
                .asset(null) // KRW 지갑
                .balance(BigDecimal.valueOf(100000)) // 기존 10만원
                .lockedBalance(BigDecimal.ZERO)
                .build();

        DividendPayoutDetail detail = DividendPayoutDetail.builder()
                .user(user)
                .walletAddress("0xWallet")
                .payoutAmount(BigDecimal.valueOf(500000)) // 50만원 배당
                .status("PENDING")
                .build();

        when(walletRepository.findKrwWalletByUserIdWithPessimisticLock(user.getId())).thenReturn(Optional.of(krwWallet));

        ItemWriter<DividendPayoutDetail> writer = dividendBatchConfig.dividendWriter();
        List<DividendPayoutDetail> details = List.of(detail);
        Chunk<DividendPayoutDetail> chunk = new Chunk<>(details);

        // When
        writer.write(chunk);

        // Then
        assertEquals(0, krwWallet.getBalance().compareTo(BigDecimal.valueOf(600000))); // 10만 + 50만 = 60만원
        assertEquals("SUCCESS", detail.getStatus());
        assertNull(detail.getErrorMessage());
        verify(walletRepository, times(1)).save(krwWallet);
        verify(dividendPayoutDetailRepository, times(1)).saveAll(details);
    }
}
