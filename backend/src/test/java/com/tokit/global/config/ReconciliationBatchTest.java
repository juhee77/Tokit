package com.tokit.global.config;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.reconciliation.entity.ReconciliationLog;
import com.tokit.domain.reconciliation.repository.ReconciliationLogRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationBatchTest {

    private ReconciliationBatchConfig reconciliationBatchConfig;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ReconciliationLogRepository reconciliationLogRepository;

    @Mock
    private ContractService contractService;

    @BeforeEach
    void setUp() {
        reconciliationBatchConfig = new ReconciliationBatchConfig(
                null, 
                null, 
                walletRepository,
                reconciliationLogRepository,
                contractService
        );
    }

    @Test
    void walletProcessor_DiscrepancyDetected_ShouldReturnLog() throws Exception {
        // Given
        User user = User.builder()
                .name("Hong Gildong")
                .email("hong@test.com")
                .walletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                .build();

        Asset asset = Asset.builder()
                .name("APPL STO")
                .symbol("APPL-STO")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .build();

        Wallet wallet = Wallet.builder()
                .user(user)
                .asset(asset)
                .balance(BigDecimal.valueOf(100))
                .lockedBalance(BigDecimal.valueOf(20))
                .build(); // Total off-chain = 120

        // On-chain returns 100 (diff: 20)
        when(contractService.balanceOfByPartition("APPL-STO", "DEFAULT", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
                .thenReturn(BigDecimal.valueOf(100));

        ItemProcessor<Wallet, ReconciliationLog> processor = reconciliationBatchConfig.walletProcessor();

        // When
        ReconciliationLog log = processor.process(wallet);

        // Then
        assertNotNull(log);
        assertEquals(user, log.getUser());
        assertEquals(asset, log.getAsset());
        assertEquals("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", log.getWalletAddress());
        assertEquals(0, log.getOffchainBalance().compareTo(BigDecimal.valueOf(120)));
        assertEquals(0, log.getOnchainBalance().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, log.getDifference().compareTo(BigDecimal.valueOf(20)));
    }

    @Test
    void walletProcessor_BalancesMatch_ShouldReturnNull() throws Exception {
        // Given
        User user = User.builder()
                .name("Hong Gildong")
                .email("hong@test.com")
                .walletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                .build();

        Asset asset = Asset.builder()
                .name("APPL STO")
                .symbol("APPL-STO")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .build();

        Wallet wallet = Wallet.builder()
                .user(user)
                .asset(asset)
                .balance(BigDecimal.valueOf(100))
                .lockedBalance(BigDecimal.valueOf(20))
                .build(); // Total off-chain = 120

        // On-chain returns 120 (Match)
        when(contractService.balanceOfByPartition("APPL-STO", "DEFAULT", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
                .thenReturn(BigDecimal.valueOf(120));

        ItemProcessor<Wallet, ReconciliationLog> processor = reconciliationBatchConfig.walletProcessor();

        // When
        ReconciliationLog log = processor.process(wallet);

        // Then
        assertNull(log);
    }

    @Test
    void reconciliationLogWriter_ShouldSaveLogs() throws Exception {
        // Given
        ReconciliationLog logEntry = ReconciliationLog.builder()
                .walletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                .offchainBalance(BigDecimal.valueOf(120))
                .onchainBalance(BigDecimal.valueOf(100))
                .difference(BigDecimal.valueOf(20))
                .build();
        
        List<ReconciliationLog> logs = List.of(logEntry);
        Chunk<ReconciliationLog> chunk = new Chunk<>(logs);

        ItemWriter<ReconciliationLog> writer = reconciliationBatchConfig.reconciliationLogWriter();

        // When
        writer.write(chunk);

        // Then
        verify(reconciliationLogRepository, times(1)).saveAll(logs);
    }
}
