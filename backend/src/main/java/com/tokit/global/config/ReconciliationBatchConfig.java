package com.tokit.global.config;

import com.tokit.domain.reconciliation.entity.ReconciliationLog;
import com.tokit.domain.reconciliation.repository.ReconciliationLogRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ReconciliationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WalletRepository walletRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final ContractService contractService;

    @Bean
    public Job reconciliationJob(Step reconciliationStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(reconciliationStep)
                .build();
    }

    @Bean
    public Step reconciliationStep() {
        return new StepBuilder("reconciliationStep", jobRepository)
                .<Wallet, ReconciliationLog>chunk(10, transactionManager)
                .reader(walletReader())
                .processor(walletProcessor())
                .writer(reconciliationLogWriter())
                .build();
    }

    @Bean
    public ItemReader<Wallet> walletReader() {
        return new RepositoryItemReaderBuilder<Wallet>()
                .name("walletReader")
                .repository(walletRepository)
                .methodName("findByAssetIsNotNull")
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(10)
                .build();
    }

    @Bean
    public ItemProcessor<Wallet, ReconciliationLog> walletProcessor() {
        return wallet -> {
            String walletAddress = wallet.getUser().getWalletAddress();
            String symbol = wallet.getAsset().getSymbol();
            
            // 1. RDBMS 상의 총 보유량 = 가용잔고 + 락된잔고
            BigDecimal offchainBalance = wallet.getBalance().add(wallet.getLockedBalance());
            
            // 2. 스마트 컨트랙트 상의 온체인 잔고 조회 (DEFAULT 파티션 기준)
            BigDecimal onchainBalance = contractService.balanceOfByPartition(symbol, "DEFAULT", walletAddress);
            
            // 3. 정합성 대조
            BigDecimal difference = offchainBalance.subtract(onchainBalance).abs();
            if (difference.compareTo(BigDecimal.ZERO) == 0) {
                // 일치하는 경우 로그로 남기지 않고 스킵
                return null; 
            }
            
            log.error("AUDIT DISCREPANCY DETECTED! User: {} ({}), Asset: {}, Off-chain: {}, On-chain: {}, Diff: {}",
                    wallet.getUser().getName(), walletAddress, symbol, offchainBalance, onchainBalance, difference);
            
            return ReconciliationLog.builder()
                    .user(wallet.getUser())
                    .asset(wallet.getAsset())
                    .walletAddress(walletAddress)
                    .offchainBalance(offchainBalance)
                    .onchainBalance(onchainBalance)
                    .difference(difference)
                    .checkedAt(LocalDateTime.now())
                    .build();
        };
    }

    @Bean
    public ItemWriter<ReconciliationLog> reconciliationLogWriter() {
        return chunk -> {
            reconciliationLogRepository.saveAll(chunk.getItems());
            log.info("Saved {} reconciliation discrepancy logs to database", chunk.getItems().size());
            // Slack/SMS 알림 모의 구현 (Alert)
            for (ReconciliationLog logEntry : chunk.getItems()) {
                sendAlertNotification(logEntry);
            }
        };
    }

    private void sendAlertNotification(ReconciliationLog logEntry) {
        log.error("[CRITICAL ALERT] [Reconciliation Auditing Discrepancy] " +
                "Balance discrepancy of {} units of {} found for User: {} (Wallet: {}). " +
                "Offchain = {}, Onchain = {}. Alert logged at {}",
                logEntry.getDifference(),
                logEntry.getAsset().getSymbol(),
                logEntry.getUser().getName(),
                logEntry.getWalletAddress(),
                logEntry.getOffchainBalance(),
                logEntry.getOnchainBalance(),
                logEntry.getCheckedAt());
    }
}
