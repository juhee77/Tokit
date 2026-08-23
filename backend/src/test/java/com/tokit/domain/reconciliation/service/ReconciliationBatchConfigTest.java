package com.tokit.domain.reconciliation.service;

import com.tokit.domain.alert.controller.AdminAlertController;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.reconciliation.entity.ReconciliationLog;
import com.tokit.domain.reconciliation.repository.ReconciliationLogRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.config.ReconciliationBatchConfig;
import com.tokit.infra.alert.SlackAlertService;
import com.tokit.infra.blockchain.ContractService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationBatchConfigTest {

    @InjectMocks
    private ReconciliationBatchConfig reconciliationBatchConfig;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ReconciliationLogRepository reconciliationLogRepository;

    @Mock
    private ContractService contractService;

    @Mock
    private SlackAlertService slackAlertService;

    @Mock
    private AdminAlertController adminAlertController;



    private User testUser;
    private Asset testAsset;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .name("Reconciliation User")
                .email("reconcile@tokit.com")
                .walletAddress("0xRECONCILE_USER_ADDRESS_77")
                .kycStatus(true)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 200L);

        testAsset = Asset.builder()
                .name("Gangnam Tower STO")
                .symbol("GANGNAM-STO")
                .totalSupply(new BigDecimal("50000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        ReflectionTestUtils.setField(testAsset, "id", 20L);

        testWallet = Wallet.builder()
                .user(testUser)
                .asset(testAsset)
                .balance(new BigDecimal("100"))      // 가용잔고 100
                .lockedBalance(new BigDecimal("50")) // 락된잔고 50 = 총 오프체인 150
                .build();
        ReflectionTestUtils.setField(testWallet, "id", 70L);
    }

    @Test
    @DisplayName("walletProcessor: 오프체인 잔고(150)와 온체인 잔고(150)가 일치하는 경우 null을 반환하여 이벤트를 무시한다.")
    void walletProcessor_WhenBalancesMatch_ReturnsNull() throws Exception {
        // Given
        when(contractService.balanceOfByPartition(eq("GANGNAM-STO"), eq("DEFAULT"), eq("0xRECONCILE_USER_ADDRESS_77")))
                .thenReturn(new BigDecimal("150")); // 일치

        ItemProcessor<Wallet, ReconciliationLog> processor = reconciliationBatchConfig.walletProcessor();

        // When
        ReconciliationLog result = processor.process(testWallet);

        // Then
        assertThat(result).isNull(); // 스킵 처리
    }

    @Test
    @DisplayName("walletProcessor: 오프체인 잔고(150)와 온체인 잔고(100) 간 오차(50) 발생 시 ReconciliationLog 객체를 생성한다.")
    void walletProcessor_WhenBalanceMismatch_ReturnsReconciliationLog() throws Exception {
        // Given
        when(contractService.balanceOfByPartition(eq("GANGNAM-STO"), eq("DEFAULT"), eq("0xRECONCILE_USER_ADDRESS_77")))
                .thenReturn(new BigDecimal("100")); // 오프체인 150 vs 온체인 100 (차이 50)

        ItemProcessor<Wallet, ReconciliationLog> processor = reconciliationBatchConfig.walletProcessor();

        // When
        ReconciliationLog result = processor.process(testWallet);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getAsset()).isEqualTo(testAsset);
        assertThat(result.getOffchainBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("150").stripTrailingZeros());
        assertThat(result.getOnchainBalance().stripTrailingZeros())
                .isEqualTo(new BigDecimal("100").stripTrailingZeros());
        assertThat(result.getDifference().stripTrailingZeros())
                .isEqualTo(new BigDecimal("50").stripTrailingZeros());
    }

    @Test
    @DisplayName("reconciliationLogWriter: 대사 오차 이력을 DB에 영속화한다.")
    void reconciliationLogWriter_PersistsLog() throws Exception {
        // Given
        ReconciliationLog logItem = ReconciliationLog.builder()
                .user(testUser)
                .asset(testAsset)
                .walletAddress("0xRECONCILE_USER_ADDRESS_77")
                .offchainBalance(new BigDecimal("150"))
                .onchainBalance(new BigDecimal("100"))
                .difference(new BigDecimal("50"))
                .build();

        ItemWriter<ReconciliationLog> writer = reconciliationBatchConfig.reconciliationLogWriter();

        // When
        writer.write(new Chunk<>(List.of(logItem)));

        // Then
        verify(reconciliationLogRepository, times(1)).saveAll(any());
        verify(slackAlertService, times(1)).sendAlert(anyString(), anyString());
        verify(adminAlertController, times(1)).broadcastAlert(anyString(), anyString());
    }
}


